package net.scalytica.clammyscan.streams

import matchers.should.Matchers.fail
import org.scalatest.matchers

object TestHelpers {

  val instreamCmd = "zINSTREAM\u0000"
  val pingCmd     = "zPING\u0000"
  val statusCmd   = "zSTATS\u0000"
  val versionCmd  = "VERSION"

  def unexpectedClamError(ce: ClamError): Nothing =
    fail(s"Unexpected ClamError result ${ce.message}")

}
