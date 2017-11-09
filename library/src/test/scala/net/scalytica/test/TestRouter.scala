package net.scalytica.test

import javax.inject.Inject

import net.scalytica.clammyscan.ClammyScanParser
import play.api.mvc.{DefaultActionBuilder, Results}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

import scala.concurrent.ExecutionContext

object TestRouterUris {

  val ScanMultiPart    = "/scanMultipart"
  val ScanTmpMultiPart = "/scanTmpMultipart"
  val ScanDirect       = "/scanDirect"
  val ScanTmpDirect    = "/scanTmpDirect"
  val Ping             = "/ping"
  val Version          = "/version"
  val Stats            = "/stats"

}

class TestRouter @Inject()(
    implicit ec: ExecutionContext,
    clammy: ClammyScanParser,
    ab: DefaultActionBuilder
) extends SimpleRouter
    with TestActions {

  // scalastyle:off line.size.limit
  // format: off
  override def routes: Routes = {
    case POST(p"/scanMultipart" ? q_?"filename=$fname" & q_?"contentType=$ctype") =>
      scanOnlyAction(clammy)

    case POST(p"/scanDirect" ? q_?"filename=$fname" & q_?"contentType=$ctype") =>
      directScanOnlyAction(clammy)

    case POST(p"/scanTmpMultipart" ? q_?"filename=$fname" & q_?"contentType=$ctype") =>
      scanTmpAction(clammy)

    case POST(p"/scanTmpDirect" ? q_?"filename=$fname" & q_?"contentType=$ctype") =>
      directTmpAction(clammy)

    case GET(p"/ping") =>
      pingAction(clammy)

    case GET(p"/version") =>
      versionAction(clammy)

    case GET(p"/stats") =>
      statsAction(clammy)

    case err => ab {
      Results.BadRequest(s"${err.method} ${err.uri} could not be found")
    }
  }

  // format: on
  // scalastyle:on line.size.limit

}
