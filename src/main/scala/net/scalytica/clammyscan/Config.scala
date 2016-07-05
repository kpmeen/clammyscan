package net.scalytica.clammyscan

import play.api.Configuration

import scala.concurrent.duration._

trait ConfigKeys {
  val hostKey = "clammyscan.clamd.host"
  val portKey = "clammyscan.clamd.port"
  val timeoutKey = "clammyscan.clamd.timeout"
  val removeInfectedKey = "clammyscan.removeInfected"
  val removeOnErrorKey = "clammyscan.removeOnError"
  val failOnErrorKey = "clammyscan.failOnError"
  val disabled = "clammyscan.scanDisabled"
  val filenameRegex = "clammyscan.validFilenameRegex"
}

class ClamConfig(config: Configuration) extends ConfigKeys {

  private val DefaultPortNumber = 3310
  private val DefaultTimeout = 5 seconds

  /**
   * IP address of clamd daemon. Defaults to localhost
   */
  lazy val host: String = config.getString(hostKey).getOrElse("localhost")

  /**
   * port of clamd daemon. Defaults to 3310
   */
  lazy val port: Int = config.getInt(portKey).getOrElse(DefaultPortNumber)

  /**
   * Socket timeout for clam. Defaults to 5 seconds.
   */
  lazy val timeout: Duration =
    config.getMilliseconds(timeoutKey).map { ms =>
      if (ms == 0) Duration.Inf else FiniteDuration(ms, MILLISECONDS)
    }.getOrElse(DefaultTimeout)

  /**
   * Remove file if it is infected... defaults value is true
   */
  lazy val canRemoveInfectedFiles: Boolean =
    config.getBoolean(removeInfectedKey).getOrElse(true)

  /**
   * Whether or not to cause the body parser to stop uploading if we
   * cannot connect to clamd... defaults to false
   */
  lazy val shouldFailOnError: Boolean =
    config.getBoolean(failOnErrorKey).getOrElse(false)

  /**
   * Remove file if an error occurred during scanning... defaults to true,
   * but will be overridden and set to false if shouldFailOnError = false
   */
  lazy val canRemoveOnError: Boolean =
    if (shouldFailOnError) false
    else config.getBoolean(removeOnErrorKey).getOrElse(true)

  /**
   * Disables all virus scanning... defaults to false
   */
  lazy val scanDisabled: Boolean =
    config.getBoolean(disabled).getOrElse(false)

  /**
   * Allows defining a regular expression to validate filenames for
   * illegal characters. A good one could e.g. be:
   * <code>
   * (.[\"\*\\\>\<\?\/\:\|].)|(.[\.]?.[\.]$)|(.*[ ]+$)
   * </code>
   *
   */
  lazy val validFilenameRegex: Option[String] =
    config.getString(filenameRegex).orElse(Option(filenameRegex))

}