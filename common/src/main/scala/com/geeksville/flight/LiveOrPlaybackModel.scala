package com.geeksville.flight

import org.mavlink.messages.MAV_TYPE
import org.mavlink.messages.MAV_AUTOPILOT
import org.mavlink.messages.MAVLinkMessage
import org.mavlink.messages.ardupilotmega.msg_vfr_hud
import org.mavlink.messages.ardupilotmega.msg_statustext
import com.geeksville.mavlink.TimestampedMessage

object LiveOrPlaybackModel {
  private val planeCodeToModeMap = Map(0 -> "MANUAL", 1 -> "CIRCLE", 2 -> "STABILIZE",
    3 -> "TRAINING",
    5 -> "FBW_A", 6 -> "FBW_B", 10 -> "AUTO",
    11 -> "RTL", 12 -> "LOITER", 15 -> "GUIDED", 16 -> "INITIALIZING")

  private val copterCodeToModeMap = Map(
    0 -> "STABILIZE",
    1 -> "ACRO",
    2 -> "ALT_HOLD",
    3 -> "AUTO",
    4 -> "GUIDED",
    5 -> "LOITER",
    6 -> "RTL",
    7 -> "CIRCLE",
    9 -> "LAND",
    10 -> "OF_LOITER",
    11 -> "DRIFT",
    13 -> "SPORT",
    14 -> "FLIP",
    15 -> "AUTOTUNE")

  /**
   * A mapping to RGB tuples (chosen to match the colors used by Tridge's python tool)
   */
  val modeToColorMap = Map(
    "MANUAL" -> (255, 0, 0),
    "AUTO" -> (0, 255, 0),
    "LOITER" -> (0, 0, 255),
    "FBWA" -> (255, 100, 0),
    "RTL" -> (255, 0, 100),
    "STABILIZE" -> (100, 255, 0),
    "LAND" -> (0, 255, 100),
    "STEERING" -> (100, 0, 255),
    "HOLD" -> (0, 100, 255),
    "ALT_HOLD" -> (255, 100, 100),
    "CIRCLE" -> (100, 255, 100),
    "GUIDED" -> (100, 100, 255),
    "ACRO" -> (255, 255, 0))

  private val roverCodeToModeMap = Map(
    0 -> "MANUAL", 2 -> "LEARNING", 3 -> "STEERING",
    4 -> "HOLD",
    10 -> "AUTO",
    11 -> "RTL", 15 -> "GUIDED", 16 -> "INITIALIZING")

  private val typeNameMap = Map(
    MAV_TYPE.MAV_TYPE_QUADROTOR -> "quadcopter",
    MAV_TYPE.MAV_TYPE_TRICOPTER -> "tricopter",
    MAV_TYPE.MAV_TYPE_COAXIAL -> "coaxial",
    MAV_TYPE.MAV_TYPE_HEXAROTOR -> "hexarotor",
    MAV_TYPE.MAV_TYPE_OCTOROTOR -> "octorotor",
    MAV_TYPE.MAV_TYPE_FIXED_WING -> "fixed-wing",
    MAV_TYPE.MAV_TYPE_GROUND_ROVER -> "ground-rover",
    MAV_TYPE.MAV_TYPE_SUBMARINE -> "submarine",
    MAV_TYPE.MAV_TYPE_AIRSHIP -> "airship",
    MAV_TYPE.MAV_TYPE_FLAPPING_WING -> "flapping-wing",
    MAV_TYPE.MAV_TYPE_SURFACE_BOAT -> "boat",
    MAV_TYPE.MAV_TYPE_FREE_BALLOON -> "free-balloon",
    MAV_TYPE.MAV_TYPE_ANTENNA_TRACKER -> "antenna-tracker",
    MAV_TYPE.MAV_TYPE_GENERIC -> "generic",
    MAV_TYPE.MAV_TYPE_ROCKET -> "rocket",
    MAV_TYPE.MAV_TYPE_HELICOPTER -> "helicopter")

  private val autopiltNameMap = Map(
    MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA -> "apm",
    MAV_AUTOPILOT.MAV_AUTOPILOT_GENERIC -> "generic",
    MAV_AUTOPILOT.MAV_AUTOPILOT_PX4 -> "px4",
    MAV_AUTOPILOT.MAV_AUTOPILOT_OPENPILOT -> "openpilot",
    MAV_AUTOPILOT.MAV_AUTOPILOT_PIXHAWK -> "pixhawk",
    MAV_AUTOPILOT.MAV_AUTOPILOT_PPZ -> "ppz",
    MAV_AUTOPILOT.MAV_AUTOPILOT_UDB -> "udb",
    MAV_AUTOPILOT.MAV_AUTOPILOT_FP -> "fp")
}

/**
 * Shared state that applies to either live (VehicleModel) or delayed (PlaybackModel) implementations
 */
trait LiveOrPlaybackModel {
  import LiveOrPlaybackModel._

  // Summary stats
  var maxAltitude: Double = 0.0
  var maxAirSpeed: Double = 0.0
  var maxGroundSpeed: Double = 0.0
  var buildName: Option[String] = None
  var buildVersion: Option[String] = None
  var buildGit: Option[String] = None

  // In usecs
  var startOfFlightTime: Option[Long] = None
  var endOfFlightTime: Option[Long] = None

  /// Start time for current mission in usecs
  var startTime: Option[Long] = None

  /// Stop time for current mission in usecs
  var currentTime: Option[Long] = None

  var vfrHud: Option[msg_vfr_hud] = None

  private val planeModeToCodeMap = planeCodeToModeMap.map { case (k, v) => (v, k) }
  private val copterModeToCodeMap = copterCodeToModeMap.map { case (k, v) => (v, k) }
  private val roverModeToCodeMap = roverCodeToModeMap.map { case (k, v) => (v, k) }

  /**
   * A set of modes that are selectable when the vehicle is flying in simple mode
   */
  protected val simpleFlightModes = Map("LAND" -> true, "RTL" -> false, "ALT_HOLD" -> false,
    "LOITER" -> false, "AUTO" -> true, "STABILIZE" -> false, "FBW_B" -> false, "DRIFT" -> false, "Disarm" -> true)

  // FIXME - add support for a takeoff waypoint set
  protected val simpleGroundModes = Map("Arm" -> true, "LOITER" -> false, "AUTO" -> true, "STABILIZE" -> false, "Disarm" -> false)

  // Modes to allow while downloading wpts or params
  protected val initializingModes = Map("Disarm" -> false)

  /// Must match ArduCopter or ArduPlane etc... used to find appropriate parameter docs
  // FIXME handle other vehicle types
  def vehicleTypeName = if (isCopter)
    "ArduCopter"
  else if (isRover)
    "APMrover2"
  else
    "ArduPlane"

  /**
   * A human readable name for this type of vehicle (if known...)
   */
  def humanVehicleType = vehicleType.flatMap(LiveOrPlaybackModel.typeNameMap.get(_))
  def humanAutopilotType = autopilotType.flatMap(LiveOrPlaybackModel.autopiltNameMap.get(_))

  def isPlane = vehicleType.map(_ == MAV_TYPE.MAV_TYPE_FIXED_WING).getOrElse(false)

  /**
   * Are we on a copter? or None if not sure
   */
  def isCopterOpt = vehicleType.map { t =>
    (t == MAV_TYPE.MAV_TYPE_QUADROTOR) || (t == MAV_TYPE.MAV_TYPE_HELICOPTER) ||
      (t == MAV_TYPE.MAV_TYPE_TRICOPTER) || (t == MAV_TYPE.MAV_TYPE_COAXIAL) ||
      (t == MAV_TYPE.MAV_TYPE_HEXAROTOR) || (t == MAV_TYPE.MAV_TYPE_OCTOROTOR)
  }

  def isCopter = isCopterOpt.getOrElse(true)
  def isRover = vehicleType.map(_ == MAV_TYPE.MAV_TYPE_GROUND_ROVER).getOrElse(false)

  /// A MAV_TYPE vehicle code
  def vehicleType: Option[Int]

  /// A MAV_AUTOPILOT code
  def autopilotType: Option[Int]

  protected def codeToModeMap = if (isPlane)
    planeCodeToModeMap
  else if (isCopter)
    copterCodeToModeMap
  else if (isRover)
    roverCodeToModeMap
  else
    Map[Int, String]()

  protected def modeToCodeMap = if (isPlane)
    planeModeToCodeMap
  else if (isCopter)
    copterModeToCodeMap
  else if (isRover)
    roverModeToCodeMap
  else
    Map[String, Int]()

  /// Convert a custom mode int into a human readable string
  def modeToString(modeCode: Int) = codeToModeMap.getOrElse(modeCode, "unknown")

  val VersionRegex = "(\\S*) (V\\S*).*(\\S*)".r

  /**
   * duration of flying portion in seconds
   */
  def flightDuration = (for {
    s <- startOfFlightTime
    e <- endOfFlightTime
  } yield {
    val r = TimestampedMessage.usecsToSeconds(e) - TimestampedMessage.usecsToSeconds(s)
    println(s"Calculated flight duration of $r")
    r
  }).orElse {
    println("Can't find duration for flight")
    None
  }

  val updateModel: PartialFunction[Any, Unit] = {
    case msg: msg_vfr_hud =>
      //println(s"Considering vfrhud: $msg")
      vfrHud = Some(msg)
      maxAirSpeed = math.max(msg.airspeed, maxAirSpeed)
      maxGroundSpeed = math.max(msg.groundspeed, maxGroundSpeed)
      if (msg.throttle > 0) {
        if (!startOfFlightTime.isDefined) {
          startOfFlightTime = startTime
        }
        endOfFlightTime = currentTime
      }
    case msg: msg_statustext =>
      // Sniff messages looking for interesting vehicle strings
      val s = msg.getText()
      //println(s"Considering status: $s")
      s match {
        case VersionRegex(bName, bVer, bGit) =>
          buildName = Some(bName)
          buildVersion = Some(bVer)
          buildGit = Some(bGit)
        case _ =>
      }

    // Messages might arrive encapsulated in a timestamped message, if so then use that for our sense of time
    case msg: TimestampedMessage =>
      // Update start/stop times
      if (!startTime.isDefined)
        startTime = Some(msg.time)

      currentTime = Some(msg.time)
  }
}