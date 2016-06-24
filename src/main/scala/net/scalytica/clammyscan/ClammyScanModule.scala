package net.scalytica.clammyscan

import play.api.inject._
import play.api.{Configuration, Environment}

class ClammyScanModule extends Module {
  // scalastyle:off
  def bindings(environment: Environment, configuration: Configuration) = {
    Seq(
      bind[ClammyScan].to[ClammyScanParser]
    )
  }
  // scalastyle:on
}
