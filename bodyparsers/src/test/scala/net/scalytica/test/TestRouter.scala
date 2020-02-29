package net.scalytica.test

import javax.inject.Inject

import net.scalytica.clammyscan.ClammyScanParser
import play.api.mvc.{DefaultActionBuilder, Results}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

import scala.concurrent.ExecutionContext

object TestRouterUris {

  val MultipartScanOnly = "/multipartScanOnly"
  val MultiPartScanTmp  = "/multipartScanTmp"
  val DirectScanOnly    = "/directScanOnly"
  val DirectScanTmp     = "/directScanTmp"
  val Ping              = "/ping"
  val Version           = "/version"
  val Stats             = "/stats"

}

class TestRouter @Inject() (
    implicit ec: ExecutionContext,
    clammy: ClammyScanParser,
    ab: DefaultActionBuilder
) extends SimpleRouter
    with TestActions {

  // scalastyle:off
  // format: off
  override def routes: Routes = {
    case POST(p"/multipartScanOnly" ? q_?"filename=${fname@_}" & q_?"contentType=${ctype@_}") =>
      multipartScanOnlyAction(clammy)

    case POST(p"/directScanOnly" ? q_?"filename=${fname@_}" & q_?"contentType=${ctype@_}") =>
      directScanOnlyAction(clammy)

    case POST(p"/multipartScanTmp" ? q_?"filename=${fname@_}" & q_?"contentType=${ctype@_}") =>
      multipartScanTmpAction(clammy)

    case POST(p"/directScanTmp" ? q_?"filename=${fname@_}" & q_?"contentType=${ctype@_}") =>
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
  // scalastyle:on

}
