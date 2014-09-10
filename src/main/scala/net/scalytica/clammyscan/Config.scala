package net.scalytica.clammyscan

import com.typesafe.config.ConfigFactory
import play.api.Play._

trait ConfigKeys {
  val hostKey = "clammyscan.clamd.host"
  val portKey = "clammyscan.clamd.port"
  val timeoutKey = "clammyscan.clamd.timeout"
  val gridfsDuplicateFilesKey = "clammyscan.gridfs.allowDuplicateFiles"
  val removeInfectedKey = "clammyscan.removeInfected"
  val removeOnErrorKey = "clammyscan.removeOnError"
  val failOnErrorKey = "clammyscan.failOnError"
  val disabled = "clammyscan.scanDisabled"
}

trait ClamConfig extends ConfigKeys {

  /*
  * IP address of clamd daemon. Defaults to localhost ("clamserver" if no play application is available)
  */
  def host: String = {
    maybeApplication.map(_.configuration.getString(hostKey).getOrElse("localhost")).getOrElse {
      ConfigFactory.load.getString(hostKey)
    }
  }

  /**
   * port of clamd daemon. Defaults to 3310
   */
  def port: Int = {
    maybeApplication.map(_.configuration.getInt(portKey).getOrElse(3310)).getOrElse {
      ConfigFactory.load.getInt(portKey)
    }
  }

  /**
   * Socket timeout for clam. Defaults to 5 seconds when running in a play application (otherwise default is 0).
   */
  def timeout: Int = {
    maybeApplication.map(_.configuration.getInt(timeoutKey).getOrElse(5000)).getOrElse {
      ConfigFactory.load.getInt(timeoutKey)
    }
  }
}

trait ClammyParserConfig extends ConfigKeys {

  /**
   * Indicates whether or not to allow storing duplicate file names... default value is true
   */
  def allowDuplicateFiles: Boolean = {
    maybeApplication.map(_.configuration.getBoolean(gridfsDuplicateFilesKey).getOrElse(true)).getOrElse {
      ConfigFactory.load.getBoolean(gridfsDuplicateFilesKey)
    }
  }

  /**
   * Remove file if it is infected... defaults value is true
   */
  def canRemoveInfectedFiles: Boolean = {
    maybeApplication.map(_.configuration.getBoolean(removeInfectedKey).getOrElse(true)).getOrElse {
      ConfigFactory.load.getBoolean(removeInfectedKey)
    }
  }

  /**
   * Whether or not to cause the body parser to stop uploading if we cannot connect to clamd... defaults to false
   */
  def shouldFailOnError: Boolean = {
    maybeApplication.map(_.configuration.getBoolean(failOnErrorKey).getOrElse(false)).getOrElse {
      ConfigFactory.load.getBoolean(failOnErrorKey)
    }
  }

  /**
   * Remove file if an error occurred during scanning... defaults to true,
   * but will be overridden and set to false if shouldFailOnError = false
   */
  def canRemoveOnError: Boolean = {
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
  def scanDisabled: Boolean = {
    maybeApplication.map(_.configuration.getBoolean(disabled).getOrElse(false)).getOrElse {
      ConfigFactory.load.getBoolean(disabled)
    }
  }

}