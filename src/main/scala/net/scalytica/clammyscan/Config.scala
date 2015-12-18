package net.scalytica.clammyscan

import com.typesafe.config.ConfigFactory
import play.api.Play._

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

  private val DefaultPortNumber = 3310
  private val DefaultTimeout = 5000

  /*
  * IP address of clamd daemon. Defaults to localhost ("clamserver" if no play application is available)
  */
  lazy val host: String = {
    maybeApplication.map(_.configuration.getString(hostKey).getOrElse("localhost")).getOrElse {
      ConfigFactory.load.getString(hostKey)
    }
  }

  /**
   * port of clamd daemon. Defaults to 3310
   */
  lazy val port: Int = {
    maybeApplication.map(_.configuration.getInt(portKey).getOrElse(DefaultPortNumber)).getOrElse {
      ConfigFactory.load.getInt(portKey)
    }
  }

  /**
   * Socket timeout for clam. Defaults to 5 seconds when running in a play application (otherwise default is 0).
   */
  lazy val timeout: Int = {
    maybeApplication.map(_.configuration.getInt(timeoutKey).getOrElse(DefaultTimeout)).getOrElse {
      ConfigFactory.load.getInt(timeoutKey)
    }
  }
}

object ClammyParserConfig extends ConfigKeys {

  /**
   * Remove file if it is infected... defaults value is true
   */
  lazy val canRemoveInfectedFiles: Boolean = {
    maybeApplication.map(_.configuration.getBoolean(removeInfectedKey).getOrElse(true)).getOrElse {
      ConfigFactory.load.getBoolean(removeInfectedKey)
    }
  }

  /**
   * Whether or not to cause the body parser to stop uploading if we cannot connect to clamd... defaults to false
   */
  lazy val shouldFailOnError: Boolean = {
    maybeApplication.map(_.configuration.getBoolean(failOnErrorKey).getOrElse(false)).getOrElse {
      ConfigFactory.load.getBoolean(failOnErrorKey)
    }
  }

  /**
   * Remove file if an error occurred during scanning... defaults to true,
   * but will be overridden and set to false if shouldFailOnError = false
   */
  lazy val canRemoveOnError: Boolean = {
    if (shouldFailOnError) {
      false
    } else {
      maybeApplication.map(_.configuration.getBoolean(removeOnErrorKey).getOrElse(true)).getOrElse {
        ConfigFactory.load.getBoolean(removeOnErrorKey)
      }
    }
  }

  /**
   * Disables all virus scanning... defaults to false
   */
  lazy val scanDisabled: Boolean = {
    maybeApplication.map(_.configuration.getBoolean(disabled).getOrElse(false)).getOrElse {
      ConfigFactory.load.getBoolean(disabled)
    }
  }

  /**
   * Allows defining a regular expression to validate filenames for illegal characters. A good one could e.g. be:
   * <code>
   * (.[\"\*\\\>\<\?\/\:\|].)|(.[\.]?.[\.]$)|(.*[ ]+$)
   * </code>
   *
   */
  lazy val validFilenameRegex: Option[String] = {
    maybeApplication.map(_.configuration.getString(filenameRegex)).getOrElse {
      Option(ConfigFactory.load.getString(filenameRegex))
    }
  }

}