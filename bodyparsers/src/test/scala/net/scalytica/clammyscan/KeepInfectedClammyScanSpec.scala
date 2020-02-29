package net.scalytica.clammyscan

import net.scalytica.test.{TestAppContext, TestResources, TestRouterUris}
import play.api.test.Helpers.NOT_ACCEPTABLE

class KeepInfectedClammyScanSpec extends TestAppContext with TestResources {

  override lazy val additionalConfig = Map(
    "clammyscan.removeInfected" -> "false"
  )

  // TODO: Verify that the files are not actually removed

  "Using ClammyScan with removeInfected set to false" should {

    "not remove the infected file from a multipart upload" in {
      val requestBody = multipart(eicarFile, Some("application/txt"))
      val result =
        post(
          uri = TestRouterUris.MultiPartScanTmp,
          fname = Some(eicarFile.fname)
        )(
          requestBody
        ).futureValue

      result.status mustBe NOT_ACCEPTABLE
      result.body must include(virusFoundIdentifier)
    }

    "not remove the infected file from a direct upload" in {
      val requestBody = eicarFile.source
      val result =
        post(
          uri = TestRouterUris.DirectScanTmp,
          fname = Some(eicarFile.fname)
        )(
          requestBody
        ).futureValue

      result.status mustBe NOT_ACCEPTABLE
      result.body must include(virusFoundIdentifier)
    }
  }
}
