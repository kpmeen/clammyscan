package net.scalytica.clammyscan.streams

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

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

class ClamConfig(config: Config) extends ConfigKeys {
  private val DefaultPortNumber      = 3310
  private val DefaultTimeout         = 5 seconds
  private val DefaultMaxStreamLength = 2097152

  /**
   * IP address of clamd daemon. Defaults to localhost
   */
  lazy val host: String =
    Option(config.getString(hostKey)).getOrElse("localhost")

  /**
   * port of clamd daemon. Defaults to 3310
   */
  lazy val port: Int =
    Option(config.getInt(portKey)).getOrElse(DefaultPortNumber)

  /**
   * Socket timeout for clam. Defaults to 5 seconds.
   */
  lazy val timeout: Duration = {
    Option(config.getDuration(timeoutKey))
      .map { ms =>
        if (ms.isZero) Duration.Inf
        else Duration(ms.toMillis, TimeUnit.MILLISECONDS)
      }
      .getOrElse(DefaultTimeout)
  }

  lazy val streamMaxLength: Int =
    if (config.hasPath(streamMaxLengthKey))
      config.getBytes(streamMaxLengthKey).toInt
    else DefaultMaxStreamLength

  /**
   * Remove file if it is infected... defaults value is true
   */
  lazy val canRemoveInfectedFiles: Boolean =
    Option(config.getBoolean(removeInfectedKey)).getOrElse(true)

  /**
   * Whether or not to cause the body parser to stop uploading if we
   * cannot connect to clamd... defaults to false
   */
  lazy val shouldFailOnError: Boolean =
    Option(config.getBoolean(failOnErrorKey)).getOrElse(false)

  /**
   * Remove file if an error occurred during scanning... defaults to true,
   * but will be overridden and set to false if shouldFailOnError = false
   */
  lazy val canRemoveOnError: Boolean =
    if (shouldFailOnError) false
    else Option(config.getBoolean(removeOnErrorKey)).getOrElse(true)

  /**
   * Disables all virus scanning... defaults to false
   */
  lazy val scanDisabled: Boolean =
    Option(config.getBoolean(disabled)).getOrElse(false)

  /**
   * Allows defining a regular expression to validate filenames for
   * illegal characters. A good one could e.g. be:
   * <code>
   * (.[\"\*\\\>\<\?\/\:\|].)|(.[\.]?.[\.]$)|(.*[ ]+$)
   * </code>
   *
   */
  lazy val validFilenameRegex: Option[String] =
    Option(config.getString(filenameRegex)).orElse(Option(filenameRegex))
}
