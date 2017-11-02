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
  )(implicit ctx: Context): Unit = {
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

      val unavailableConf: Map[String, Any] =
        Map("clammyscan.clamd.port" -> "3333")

      "fail when scanOnly is used" in
        withScanAction(scanOnlyAction, unavailableConf) { implicit ctx =>
          val request =
            fakeMultipartRequest(eicarZipFile, Some("application/zip"))
          val result = ctx.awaitResult(request)

          validateResult(result, BAD_REQUEST, clamdUnavailableResult)
        }

      "fail the AV scan and keep the file when scanWithTmpFile is used" in
        withScanAction(scanTmpAction, unavailableConf) { implicit ctx =>
          val request = fakeMultipartRequest(eicarFile, None)
          val result  = ctx.awaitResult(request)

          validateResult(result, BAD_REQUEST, clamdUnavailableResult)
        }

      "fail when directScanOnly is used" in
        withScanAction(directScanOnlyAction, unavailableConf) { implicit ctx =>
          val request = fakeDirectRequest(eicarFile, None)
          val result  = ctx.awaitResult(request)

          validateResult(result, BAD_REQUEST, clamdUnavailableResult)
        }
    }

    "scanning is disabled" should {
      val disabledConfig: Map[String, Any] =
        Map("clammyscan.scanDisabled" -> true)

      "return OK when only scanning multipart file" in
        withScanAction(scanOnlyAction, disabledConfig) { implicit ctx =>
          val request =
            fakeMultipartRequest(eicarZipFile, Some("application/zip"))
          val result = ctx.awaitResult(request)

          validateResult(result, OK, None)
        }

      "skip the AV scan an keep the file when uploading multipart file" in
        withScanAction(scanTmpAction, disabledConfig) { implicit ctx =>
          val request = fakeMultipartRequest(eicarFile, Some("application/txt"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)
        }

      "return OK when only scanning direct upload file" in
        withScanAction(directScanOnlyAction, disabledConfig) { implicit ctx =>
          val request = fakeDirectRequest(eicarZipFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)
        }
    }
  }

  "A ClammyScan with default configuration" which {

    "receives a multipart file for scanning only" should {
      "scan infected file and not persist the file" in
        withScanAction(scanOnlyAction) { implicit ctx =>
          val request =
            fakeMultipartRequest(eicarZipFile, Some("application/zip"))
          val result = ctx.awaitResult(request)

          validateResult(result, NOT_ACCEPTABLE, eicarResult)
        }

      "scan clean file and not persist the file" in
        withScanAction(scanOnlyAction) { implicit ctx =>
          val request = fakeMultipartRequest(cleanFile, Some("application/pdf"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)
        }

      "fail scanning file when size is larger than clam config" in
        withScanAction(scanOnlyAction) { implicit ctx =>
          val request = fakeMultipartRequest(largeFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(
            result,
            BAD_REQUEST,
            Some(ClamProtocol.MaxSizeExceededResponse)
          )
        }
    }

    "recieves a direct upload file for scanning only" should {
      "scan infected file and not persist the file" in
        withScanAction(directScanOnlyAction) { implicit ctx =>
          val request = fakeDirectRequest(eicarZipFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(result, NOT_ACCEPTABLE, eicarResult)
        }

      "scan clean file and not persist the file" in
        withScanAction(directScanOnlyAction) { implicit ctx =>
          val request = fakeDirectRequest(cleanFile, Some("application/pdf"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)
        }

      "fail scanning file when size is larger than clam config" in
        withScanAction(directScanOnlyAction) { implicit ctx =>
          val request = fakeDirectRequest(largeFile, Some("application/zip"))
          val result  = ctx.awaitResult(request)

          validateResult(
            result,
            BAD_REQUEST,
            Some(ClamProtocol.MaxSizeExceededResponse)
          )
        }
    }

    "receives a multipart file for scanning and saving as temp file" should {

      "scan infected file and remove the temp file" in
        withScanAction(scanTmpAction) { implicit ctx =>
          val request = fakeMultipartRequest(eicarFile, None)
          val result  = ctx.awaitResult(request)

          validateResult(result, NOT_ACCEPTABLE, eicarResult)
        }

      "scan clean file and not remove the temp file" in
        withScanAction(scanTmpAction) { implicit ctx =>
          val request = fakeMultipartRequest(cleanFile, Some("application/pdf"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)
        }

      "reject the file if the name doesn't comply to filename rules" in
        withScanAction(scanTmpAction) { implicit ctx =>
          val bad = "some:<bad>?filename"
          val request = fakeMultipartRequest(
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
          )
        }
    }

    "receives a direct file for scanning and saving as temp file" should {

      "scan infected file and remove the temp file" in
        withScanAction(directTmpAction) { implicit ctx =>
          val request = fakeDirectRequest(eicarFile, None)
          val result  = ctx.awaitResult(request)

          validateResult(result, NOT_ACCEPTABLE, eicarResult)
        }

      "scan clean file and not remove the temp file" in
        withScanAction(directTmpAction) { implicit ctx =>
          val request = fakeDirectRequest(cleanFile, Some("application/pdf"))
          val result  = ctx.awaitResult(request)

          validateResult(result, OK, None)
        }

      "reject the file if the name doesn't comply to filename rules" in
        withScanAction(directTmpAction) { implicit ctx =>
          val bad = "some:<bad>?filename"
          val request = fakeDirectRequest(
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
          )
        }
    }
  }

  "A ClammyScan with removeInfected set to false" which {

    val doNotRemoveInfectedConfig: Map[String, Any] =
      Map("clammyscan.removeInfected" -> false)

    "recieves a multipart file upload" should {
      "not remove the infected file" in
        withScanAction(scanTmpAction, doNotRemoveInfectedConfig) {
          implicit ctx =>
            val request = fakeMultipartRequest(eicarFile, None)
            val result  = ctx.awaitResult(request)

            validateResult(result, NOT_ACCEPTABLE, eicarResult)
        }
    }

    "recieves a direct file upload" should {
      "not remove the infected file" in
        withScanAction(directTmpAction, doNotRemoveInfectedConfig) {
          implicit ctx =>
            val request = fakeDirectRequest(eicarFile, None)
            val result  = ctx.awaitResult(request)

            validateResult(result, NOT_ACCEPTABLE, eicarResult)
        }
    }
  }
}
