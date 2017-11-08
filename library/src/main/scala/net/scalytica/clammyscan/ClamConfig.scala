package net.scalytica.clammyscan

import play.api.Configuration

import scala.concurrent.duration._

trait ConfigKeys {
  val hostKey            = "clammyscan.clamd.host"
  val portKey            = "clammyscan.clamd.port"
  val timeoutKey         = "clammyscan.clamd.timeout"
  val streamMaxLengthKey = "clammyscan.clamd.streamMaxLength"
  val removeInfectedKey  = "clammyscan.removeInfected"
  val removeOnErrorKey   = "clammyscan.removeOnError"
  val failOnErrorKey     = "clammyscan.failOnError"
  val disabled           = "clammyscan.scanDisabled"
  val filenameRegex      = "clammyscan.validFilenameRegex"
}

class ClamConfig(config: Configuration) extends ConfigKeys {

  private val DefaultPortNumber      = 3310
  private val DefaultTimeout         = 5 seconds
  private val DefaultMaxStreamLength = 2097152

  /**
   * IP address of clamd daemon. Defaults to localhost
   */
  lazy val host: String =
    config.getOptional[String](hostKey).getOrElse("localhost")

  /**
   * port of clamd daemon. Defaults to 3310
   */
  lazy val port: Int =
    config.getOptional[Int](portKey).getOrElse(DefaultPortNumber)

  /**
   * Socket timeout for clam. Defaults to 5 seconds.
   */
  lazy val timeout: Duration =
    config
      .getOptional[Duration](timeoutKey)
      .map(ms => if (ms._1 == 0) Duration.Inf else ms)
      .getOrElse(DefaultTimeout)

  lazy val streamMaxLength: Int =
    if (config.has(streamMaxLengthKey))
      config.underlying.getBytes(streamMaxLengthKey).toInt
    else DefaultMaxStreamLength

  /**
   * Remove file if it is infected... defaults value is true
   */
  lazy val canRemoveInfectedFiles: Boolean =
    config.getOptional[Boolean](removeInfectedKey).getOrElse(true)

  /**
   * Whether or not to cause the body parser to stop uploading if we
   * cannot connect to clamd... defaults to false
   */
  lazy val shouldFailOnError: Boolean =
    config.getOptional[Boolean](failOnErrorKey).getOrElse(false)

  /**
   * Remove file if an error occurred during scanning... defaults to true,
   * but will be overridden and set to false if shouldFailOnError = false
   */
  lazy val canRemoveOnError: Boolean =
    if (shouldFailOnError) false
    else config.getOptional[Boolean](removeOnErrorKey).getOrElse(true)

  /**
   * Disables all virus scanning... defaults to false
   */
  lazy val scanDisabled: Boolean =
    config.getOptional[Boolean](disabled).getOrElse(false)

  /**
   * Allows defining a regular expression to validate filenames for
   * illegal characters. A good one could e.g. be:
   * <code>
   * (.[\"\*\\\>\<\?\/\:\|].)|(.[\.]?.[\.]$)|(.*[ ]+$)
   * </code>
   *
   */
  lazy val validFilenameRegex: Option[String] =
    config.getOptional[String](filenameRegex).orElse(Option(filenameRegex))

}
