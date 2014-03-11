package com.geeksville.apiproxy

/**
 * These are low level routines called by the GCS to hook into the proxy. When
 * the proxy calls in the expected sequence of operations are:
 *
 * loginUser
 *
 * setVehicleId (must be done before any data is sent from that vehicle)
 *
 * filterMavlink (for each packet)
 *
 * @author kevinh
 *
 */
trait GCSHooks {
  /**
   * Provide the callbacks for the GCS. GCS must call this routine before
   * calling any other API functions.
   *
   * @param cb
   */
  // def setCallback( cb: GCSCallback)

  /**
   * GCS must call this for ever mavlink packet received or sent from the
   * vehicle
   *
   * @param bytes
   *            the packet
   * @param fromInterface
   *            the interface # this data arrived on (or -1 if generated by
   *            the GCS itself)
   * @throws IOException
   */
  def filterMavlink(fromInterface: Int, bytes: Array[Byte])

  /**
   * Connect to web service
   *
   * @param userName
   * @param password
   * @throws IOException
   * @throws UnknownHostException
   */
  def loginUser(userName: String, password: String)

  /**
   * Associate a server vehicleId string with a particular mavlink sysId. GCS
   * must call this for every vehicle that is connected.
   *
   * @param vehicleId
   *            a UUID for this vehicle, if the server has never seen this
   *            UUID before, a new vehicle record will be created. Use the
   *            special string "gcs" for data from the GCS (not really a
   *            vehicle)
   * @param fromInterface
   *            the interface # this vehicle is connected on
   * @param mavlinkSysId
   *            the mavlink sysid for this vehicle
   * @throws IOException
   */
  def setVehicleId(vehicleId: String, fromInterface: Int, mavlinkSysId: Int)

  /**
   * Send any queued messages immedately
   */
  def flush()

  /**
   * Disconnects from web service
   */
  def close()

}