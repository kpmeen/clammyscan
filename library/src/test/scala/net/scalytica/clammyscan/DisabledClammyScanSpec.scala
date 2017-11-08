package net.scalytica.clammyscan

import net.scalytica.test.{TestAppContext, TestResources, TestRouterUris}
import play.api.test.Helpers.OK

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
    }
  }
}
