package net.scalytica.clammyscan

import net.scalytica.test.{TestAppContext, TestResources, TestRouterUris}
import play.api.test.Helpers._

class DisabledClammyScanSpec extends TestAppContext with TestResources {

  override lazy val additionalConfig = Map("clammyscan.scanDisabled" -> "true")

  "Using ClammyScan" when {

    "scanning is disabled" should {

      "return OK when only scanning multipart file" in {
        val requestBody = multipart(eicarZipFile, Some("application/zip"))
        val result =
          post(TestRouterUris.ScanMultiPart, Some(eicarZipFile.fname))(
            requestBody
          ).futureValue

        result.status mustBe OK
      }

      "skip the AV scan an keep the file when uploading multipart file" in {
        val requestBody = multipart(eicarFile, Some("application/txt"))
        val result =
          post(TestRouterUris.ScanTmpMultiPart, Some(eicarFile.fname))(
            requestBody
          ).futureValue

        result.status mustBe OK
      }

      "return OK when only scanning direct upload file" in {
        val requestBody = eicarZipFile.source
        val result =
          post(
            TestRouterUris.ScanDirect,
            Some(eicarZipFile.fname),
            Some("application/zip")
          )(
            requestBody
          ).futureValue

        result.status mustBe OK
      }

      "skip the AV scan an keep the file when uploading direct file" in {
        val requestBody = eicarZipFile.source
        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(eicarZipFile.fname),
            Some("application/zip")
          )(
            requestBody
          ).futureValue

        result.status mustBe OK
      }

      "still respond to the ping command" in {
        val res = wsUrl(TestRouterUris.Ping).get().futureValue

        res.status mustBe OK
        res.contentType mustBe JSON
        res.body mustBe PingResult
      }

      "still respond to the version command" in {
        val res = wsUrl(TestRouterUris.Version).get().futureValue

        res.status mustBe OK
        res.contentType mustBe JSON
        res.body must include regex ExpectedVersionStr
      }

      "still respond to the stats command" in {
        val res = wsUrl(TestRouterUris.Stats).get().futureValue

        res.status mustBe OK
        res.contentType mustBe JSON
        res.body must include regex ExpectedVersionStr
      }
    }
  }
}
