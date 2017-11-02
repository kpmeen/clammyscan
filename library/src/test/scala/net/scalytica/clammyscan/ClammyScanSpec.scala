package net.scalytica.clammyscan

import play.api.mvc.Result
import play.api.test.Helpers._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ClammyScanSpec extends ClammyTestContext with TestResources {
  /*
    FIXME: Current tests are not testing the streaming aspect.

    The spec should be modified to extend PlaySpec and GuiceOneServerPerSuite.

    This will allow testing using a fake application with custom routes as
    follows:

    override def fakeApplication(): Application =
      new GuiceApplicationBuilder()
        .disable[EhCacheModule]
        .router(
          Router.from {
            case POST(p"/scanOnly")    => scanOnlyAction
            case POST(p"/scanTmpFile") => scanTmpAction
            case ...
          }
        )
        .build()

   */

  /**
   * IMPORTANT: This function relies heavily on the validation done in the
   * `ClammyContext.scan*Action` helpers.
   */
  def validateResult(
      result: Result,
      expectedStatusCode: Int,
      expectedBody: Option[String] = None
  )(ctx: Context): Unit = {
    implicit val mat = ctx.materializer

    result.header.status mustEqual expectedStatusCode
    expectedBody.foreach { eb =>
      val body = Await.result(
        result.body.consumeData.map[String](_.utf8String),
        20 seconds
      )
      body must include(eb)
    }
  }

  "Using ClammyScan" when {

    "clamd is not available" should {

      val unavailableConfig: Map[String, Any] =
        Map("clammyscan.clamd.port" -> "3333")

      "fail when scanOnly is used" in
        withScanAction(scanOnlyAction, unavailableConfig) { implicit ctx =>
          val request = fakeReq(eicarZipFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(result, BAD_REQUEST, clamdUnavailableResult)(ctx)
        }

      "fail the AV scan and keep the file when scanWithTmpFile is used" in
        withScanAction(scanTmpAction, unavailableConfig) { implicit ctx =>
          val request = fakeReq(eicarFile, None)
          val result  = ctx.awaitResult(request)

          validateResult(result, BAD_REQUEST, clamdUnavailableResult)(ctx)
        }
    }

    "scanning is disabled" should {
      val disabledConfig: Map[String, Any] =
        Map("clammyscan.scanDisabled" -> true)

      "return OK when only scanning" in {
        withScanAction(scanTmpAction, disabledConfig) { implicit ctx =>
          val request = fakeReq(eicarZipFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)(ctx)
        }
      }
      "skip the AV scan an keep the file" in {
        withScanAction(scanTmpAction, disabledConfig) { implicit ctx =>
          val request = fakeReq(eicarFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)(ctx)
        }
      }
    }
  }

  "A ClammyScan with default configuration" which {

    "receives a multipart file for scanning only" should {
      "scan infected file and not persist the file" in
        withScanAction(scanOnlyAction) { implicit ctx =>
          val request = fakeReq(eicarZipFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(result, NOT_ACCEPTABLE, eicarResult)(ctx)
        }

      "scan clean file and not persist the file" in
        withScanAction(scanOnlyAction) { implicit ctx =>
          val request = fakeReq(cleanFile, Some("application/pdf"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)(ctx)
        }

      "fail scanning when file is larger than clam config" in
        withScanAction(scanOnlyAction) { implicit ctx =>
          val request = fakeReq(largeFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(
            result,
            BAD_REQUEST,
            Some(ClamProtocol.MaxSizeExceededResponse)
          )(ctx)
        }
    }

    "receives a multipart file for scanning and saving as temp file" should {

      "scan infected file and remove the temp file" in
        withScanAction(scanTmpAction) { implicit ctx =>
          val request = fakeReq(eicarFile, None)
          val result  = ctx.awaitResult(request)

          validateResult(result, NOT_ACCEPTABLE, eicarResult)(ctx)
        }

      "scan clean file and not remove the temp file" in
        withScanAction(scanTmpAction) { implicit ctx =>
          val request = fakeReq(cleanFile, Some("application/pdf"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)(ctx)
        }

      "reject the file if the name doesn't comply to filename rules" in
        withScanAction(scanTmpAction) { implicit ctx =>
          val bad = "some:<bad>?filename"
          val request = fakeReq(
            fileSource = cleanFile,
            contentType = Some("application/pdf"),
            alternativeFilename = Some(bad)
          )
          val result = ctx.awaitResult(request)

          validateResult(
            result,
            BAD_REQUEST,
            Some(
              s"""{"message":"Filename $bad contains illegal characters"}"""
            )
          )(ctx)
        }
    }
  }

  "A ClammyScan with removeInfected set to false" which {

    val doNotRemoveInfectedConfig: Map[String, Any] =
      Map("clammyscan.removeInfected" -> false)

    "should not remove the infected file" in
      withScanAction(scanTmpAction, doNotRemoveInfectedConfig) { implicit ctx =>
        val request = fakeReq(eicarFile, None)
        val result  = ctx.awaitResult(request)

        validateResult(result, NOT_ACCEPTABLE, eicarResult)(ctx)
      }
  }
}
