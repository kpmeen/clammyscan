package net.scalytica.clammyscan

import net.scalytica.test.{TestAppContext, TestResources, TestRouterUris}
import play.api.test.Helpers._

class UnavailableClammyScanSpec extends TestAppContext with TestResources {

  override lazy val additionalConfig = Map("clammyscan.clamd.port" -> "3333")

  "Using ClammyScan" when {

    // TODO: Verify that the files are not actually removed

    "clamd is not available" should {

      "fail when only scanning a multipart file" in {
        val requestBody = multipart(eicarZipFile, Some("application/zip"))
        val result =
          post(TestRouterUris.ScanMultiPart, Some(eicarZipFile.fname))(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(clamdUnavailableResult.value)
      }

      "fail the scanning and keep the multipart file" in {
        val requestBody = multipart(eicarFile, Some("application/txt"))
        val result =
          post(TestRouterUris.ScanTmpMultiPart, Some(eicarFile.fname))(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(clamdUnavailableResult.value)
      }

      "fail when only scanning a direct file" in {
        val requestBody = eicarZipFile.source
        val result =
          post(
            TestRouterUris.ScanDirect,
            Some(eicarZipFile.fname),
            Some("application/zip")
          )(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(clamdUnavailableResult.value)
      }

      "fail the scanning and keep the direct file" in {
        val requestBody = eicarZipFile.source
        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(eicarZipFile.fname),
            Some("application/zip")
          )(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(clamdUnavailableResult.value)
      }

      "fail respond to the ping command" in {
        wsUrl(TestRouterUris.Ping)
          .get()
          .futureValue
          .status mustBe INTERNAL_SERVER_ERROR
      }

      "fail respond to the version command" in {
        wsUrl(TestRouterUris.Version)
          .get()
          .futureValue
          .status mustBe INTERNAL_SERVER_ERROR
      }

      "fail respond to the stats command" in {
        wsUrl(TestRouterUris.Stats)
          .get()
          .futureValue
          .status mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
