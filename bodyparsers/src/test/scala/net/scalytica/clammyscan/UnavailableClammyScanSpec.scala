package net.scalytica.clammyscan

import net.scalytica.test.{TestAppContext, TestResources, TestRouterUris}
import play.api.test.Helpers._
import play.api.test.WsTestClient

class UnavailableClammyScanSpec extends TestAppContext with TestResources {

  override lazy val additionalConfig = Map("clammyscan.clamd.port" -> "3333")

  // TODO: Verify that the files are not actually removed

  "Using ClammyScan when clamd is not available" should {

    "fail when only scanning a multipart file" in {
      val requestBody = multipart(eicarZipFile, Some("application/zip"))
      val result =
        post(
          uri = TestRouterUris.MultipartScanOnly,
          fname = Some(eicarZipFile.fname)
        )(
          requestBody
        ).futureValue

      result.status mustBe BAD_REQUEST
      result.body must include(clamdUnavailableResult.value)
    }

    "fail the scanning and keep the multipart file" in {
      val requestBody = multipart(eicarFile, Some("application/txt"))
      val result =
        post(
          uri = TestRouterUris.MultiPartScanTmp,
          fname = Some(eicarFile.fname)
        )(
          requestBody
        ).futureValue

      result.status mustBe BAD_REQUEST
      result.body must include(clamdUnavailableResult.value)
    }

    "fail when only scanning a direct file" in {
      val requestBody = eicarZipFile.source
      val result =
        post(
          uri = TestRouterUris.DirectScanOnly,
          fname = Some(eicarZipFile.fname),
          ctype = Some("application/zip")
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
          uri = TestRouterUris.DirectScanTmp,
          fname = Some(eicarZipFile.fname),
          ctype = Some("application/zip")
        )(
          requestBody
        ).futureValue

      result.status mustBe BAD_REQUEST
      result.body must include(clamdUnavailableResult.value)
    }

    "fail respond to the ping command" in {
      WsTestClient
        .wsUrl(TestRouterUris.Ping)
        .get()
        .futureValue
        .status mustBe INTERNAL_SERVER_ERROR
    }

    "fail respond to the version command" in {
      WsTestClient
        .wsUrl(TestRouterUris.Version)
        .get()
        .futureValue
        .status mustBe INTERNAL_SERVER_ERROR
    }

    "fail respond to the stats command" in {
      WsTestClient
        .wsUrl(TestRouterUris.Stats)
        .get()
        .futureValue
        .status mustBe INTERNAL_SERVER_ERROR
    }
  }
}
