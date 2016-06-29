package net.scalytica.clammyscan

import net.scalytica.clammyscan.MultipartFormDataWriteable.acAsMultiPartWritable
import play.api.test.Helpers._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ClammyScanSpec extends ClammyContext with TestResources {

  val unavailableConfig: Map[String, Any] =
    Map("clammyscan.clamd.port" -> "3333")

  "Using ClammyScan" when {

    "clamd is not available" should {

      "fail when scanOnly is used" in
        withScanAction(scanOnlyAction, unavailableConfig) { implicit ctx =>
          val request = fakeReq(eicarZipFile, Some("application/zip"))
          val result = ctx.awaitResult(request)

          result.header.status mustEqual BAD_REQUEST
        }

      "fail the AV scan and keep the file when scanWithTmpFile is used" in
        withScanAction(scanTmpAction, unavailableConfig) { implicit ctx =>
          val request = fakeReq(eicarFile, None)
          val result = ctx.awaitResult(request)

          result.header.status mustEqual BAD_REQUEST
        }
    }
  }

  "A ClammyScan with default configuration" which {

    "receives a file for scanning only" should {
      "scan infected file and not persist the file" in
        withScanAction(scanOnlyAction) { implicit ctx =>
          val request = fakeReq(eicarZipFile, Some("application/zip"))
          val result = ctx.awaitResult(request)

          result.header.status mustEqual NOT_ACCEPTABLE
        }

      "scan clean file and not persist the file" in
        withScanAction(scanOnlyAction) { implicit ctx =>
          val request = fakeReq(cleanFile, Some("application/pdf"))
          val result = ctx.awaitResult(request)

          result.header.status mustEqual OK
        }
    }

    "receives a file for scanning and saving as temp file" should {

      "scan infected file and remove the temp file" in
        withScanAction(scanTmpAction) { implicit ctx =>
          val request = fakeReq(eicarFile, None)
          val result = ctx.awaitResult(request)

          result.header.status mustEqual NOT_ACCEPTABLE
        }

      "scan clean file and not remove the temp file" in
        withScanAction(scanTmpAction) { implicit ctx =>
          val request = fakeReq(cleanFile, Some("application/pdf"))
          val result = ctx.awaitResult(request)

          implicit val mat = ctx.materializer

          result.header.status mustEqual OK
          val body = Await.result(
            result.body.consumeData.map[String](_.utf8String), 20 seconds
          )
          body must startWith("filename: multipartBody")
          body must endWith("scanWithTempFile")
        }
    }
  }
}
