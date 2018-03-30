package net.scalytica.clammyscan

import play.api.inject._
import play.api.{Configuration, Environment}

/**
 * Play module to enable the ClammyScan module using DI
 */
class ClammyScanModule extends Module {

  def bindings(
      environment: Environment,
      configuration: Configuration
  ): Seq[Binding[ClammyScan]] = Seq(bind[ClammyScan].to[ClammyScanParser])

}
