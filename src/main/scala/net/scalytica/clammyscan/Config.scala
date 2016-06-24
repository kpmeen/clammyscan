package net.scalytica.clammyscan

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import play.api.Play

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

object ClamConfig extends ConfigKeys {

  lazy val underlying = Play.maybeApplication.map { app =>
    app.configuration.underlying
  }.getOrElse(ConfigFactory.load)

  private val DefaultPortNumber = 3310
  private val DefaultTimeout = 5 seconds

  /**
   * IP address of clamd daemon. Defaults to localhost
   */
  lazy val host: String =
    underlying.as[Option[String]](hostKey).getOrElse("localhost")

  /**
   * port of clamd daemon. Defaults to 3310
   */
  lazy val port: Int =
    underlying.as[Option[Int]](portKey).getOrElse(DefaultPortNumber)

  /**
   * Socket timeout for clam. Defaults to 5 seconds.
   */
  lazy val timeout: Duration =
    underlying.as[Option[FiniteDuration]](timeoutKey)
      .map(l => if (l.length == 0) Duration.Inf else l)
      .getOrElse(DefaultTimeout)

  /**
   * Remove file if it is infected... defaults value is true
   */
  lazy val canRemoveInfectedFiles: Boolean =
    underlying.as[Option[Boolean]](removeInfectedKey).getOrElse(true)

  /**
   * Whether or not to cause the body parser to stop uploading if we
   * cannot connect to clamd... defaults to false
   */
  lazy val shouldFailOnError: Boolean =
    underlying.as[Option[Boolean]](failOnErrorKey).getOrElse(false)

  /**
   * Remove file if an error occurred during scanning... defaults to true,
   * but will be overridden and set to false if shouldFailOnError = false
   */
  lazy val canRemoveOnError: Boolean =
    if (shouldFailOnError) false
    else underlying.as[Option[Boolean]](removeOnErrorKey).getOrElse(true)

  /**
   * Disables all virus scanning... defaults to false
   */
  lazy val scanDisabled: Boolean =
    underlying.as[Option[Boolean]](disabled).getOrElse(false)

  /**
   * Allows defining a regular expression to validate filenames for
   * illegal characters. A good one could e.g. be:
   * <code>
   * (.[\"\*\\\>\<\?\/\:\|].)|(.[\.]?.[\.]$)|(.*[ ]+$)
   * </code>
   *
   */
  lazy val validFilenameRegex: Option[String] =
    underlying.as[Option[String]](filenameRegex).orElse(Option(filenameRegex))

}