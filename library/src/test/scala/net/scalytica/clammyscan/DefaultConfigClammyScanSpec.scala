package net.scalytica.clammyscan

import net.scalytica.test.{TestAppContext, TestResources, TestRouterUris}
import org.scalatest.Retries
import org.scalatest.tagobjects.Retryable
import org.scalatest.time.{Seconds, Span}
import play.api.test.Helpers._

class DefaultConfigClammyScanSpec
    extends TestAppContext
    with Retries
    with TestResources {

  // scalastyle:off magic.number
  val delay: Span = Span(5, Seconds)
  // scalastyle:on magic.number

  override def withFixture(test: NoArgTest) = {
    if (isRetryable(test)) withRetryOnFailure(delay)(super.withFixture(test))
    else super.withFixture(test)
  }

  "ClammyScan with default configuration" that {

    "receives a multipart file for scanning only" should {
      "scan infected file and not persist the file" in {
        val requestBody = multipart(eicarZipFile, Some("application/zip"))
        val result =
          post(TestRouterUris.ScanTmpMultiPart, Some(eicarZipFile.fname))(
            requestBody
          ).futureValue

        result.status mustBe NOT_ACCEPTABLE
        result.body must include(virusFoundIdentifier)
      }

      "scan clean file and not persist the file" in {
        val requestBody = multipart(cleanFile, Some("application/pdf"))
        val result =
          post(
            TestRouterUris.ScanTmpMultiPart,
            Some(cleanFile.fname),
            Some("application/pdf")
          )(
            requestBody
          ).futureValue

        result.status mustBe OK
      }

      // scalastyle:off line.size.limit
      "fail scanning file when size is larger than clam config" taggedAs Retryable in {
        val requestBody = multipart(largeFile, Some("application/zip"))
        val result =
          post(
            TestRouterUris.ScanTmpMultiPart,
            Some(largeFile.fname),
            Some("application/zip")
          )(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(ClamProtocol.MaxSizeExceededResponse)
      }
      // scalastyle:on line.size.limit

      "fail scanning an empty multipart file" taggedAs Retryable in {
        val requestBody = multipart(emptyFile, Some("application/text"))

        val result =
          post(
            TestRouterUris.ScanTmpMultiPart,
            Some(emptyFile.fname),
            Some("application/text")
          )(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(CannotScanEmptyFile.message)
      }
    }

    "receives a direct upload file for scanning only" should {
      "scan infected file and not persist the file" in {
        val requestBody = eicarZipFile.source
        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(eicarZipFile.fname),
            Some("application/zip")
          )(
            requestBody
          ).futureValue

        result.status mustBe NOT_ACCEPTABLE
        result.body must include(virusFoundIdentifier)
      }

      "scan clean file and not persist the file" in {
        val requestBody = cleanFile.source
        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(cleanFile.fname),
            Some("application/pdf")
          )(
            requestBody
          ).futureValue

        result.status mustBe OK
      }

      // scalastyle:off line.size.limit
      "fail scanning file when size is larger than clam config" taggedAs Retryable in {
        val requestBody = largeFile.source
        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(largeFile.fname),
            Some("application/zip")
          )(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(ClamProtocol.MaxSizeExceededResponse)
      }
      // scalastyle:off line.size.limit

      "fail scanning an empty direct upload file" taggedAs Retryable in {
        val requestBody = emptyFile.source

        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(emptyFile.fname),
            Some("application/text")
          )(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(CannotScanEmptyFile.message)
      }
    }

    "receives a multipart file for scanning and saving as temp file" should {
      "scan infected file and remove the temp file" in {
        val requestBody = multipart(eicarFile, Some("application/txt"))
        val result =
          post(
            TestRouterUris.ScanTmpMultiPart,
            Some(eicarFile.fname)
          )(
            requestBody
          ).futureValue

        result.status mustBe NOT_ACCEPTABLE
        result.body must include(virusFoundIdentifier)
      }

      "scan clean file and not remove the temp file" in {
        val requestBody = multipart(cleanFile, Some("application/pdf"))
        val result =
          post(
            TestRouterUris.ScanTmpMultiPart,
            Some(cleanFile.fname)
          )(
            requestBody
          ).futureValue

        result.status mustBe OK
      }

      "reject the file if the name doesn't comply to filename rules" in {
        val bad = "some:<bad>?filename"
        val requestBody =
          multipart(cleanFile, Some("application/pdf"), Some(bad))
        val result =
          post(
            TestRouterUris.ScanTmpMultiPart,
            Some(bad),
            Some("application/pdf")
          )(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(
          s"""{"message":"Filename $bad contains illegal characters"}"""
        )
      }
    }

    "receives a direct file for scanning and saving as temp file" should {

      "scan infected file and remove the temp file" in {
        val requestBody = eicarFile.source
        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(eicarFile.fname)
          )(
            requestBody
          ).futureValue

        result.status mustBe NOT_ACCEPTABLE
        result.body must include(virusFoundIdentifier)
      }

      "scan clean file and not remove the temp file" in {
        val requestBody = cleanFile.source
        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(cleanFile.fname)
          )(
            requestBody
          ).futureValue

        result.status mustBe OK
      }

      "reject the file if the name doesn't comply to filename rules" in {
        val bad         = "some:<bad>?filename"
        val requestBody = cleanFile.source
        val result =
          post(
            TestRouterUris.ScanTmpDirect,
            Some(bad),
            Some("application/pdf")
          )(
            requestBody
          ).futureValue

        result.status mustBe BAD_REQUEST
        result.body must include(
          s"""{"message":"Filename $bad contains illegal characters"}"""
        )
      }
    }

    "executing commands" should {

      "respond to the ping command" in {
        val res = wsUrl(TestRouterUris.Ping).get().futureValue

        res.status mustBe OK
        res.contentType mustBe JSON
        res.body mustBe PingResult
      }

      "respond to the version command" in {
        val res = wsUrl(TestRouterUris.Version).get().futureValue

        res.status mustBe OK
        res.contentType mustBe JSON
        res.body must include(ExpectedVersionStr)
      }

      "respond to the stats command" in {
        val res = wsUrl(TestRouterUris.Stats).get().futureValue

        res.status mustBe OK
        res.contentType mustBe JSON
        res.body must include(ExpectedVersionStr)
      }
    }
  }
}
