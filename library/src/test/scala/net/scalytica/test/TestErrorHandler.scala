package net.scalytica.test

import javax.inject.Singleton

import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Results}

import scala.concurrent.Future

@Singleton
class TestErrorHandler extends HttpErrorHandler {

  def onClientError(
      request: RequestHeader,
      statusCode: Int,
      message: String
  ) = Future.successful {
    Results.Status(statusCode)(
      s"Could not serve request: ${request.uri} because:\n$message"
    )
  }

  def onServerError(request: RequestHeader, exception: Throwable) = {
    Future.successful {
      Results.InternalServerError(
        s"A server error occurred: ${exception.getMessage}"
      )
    }
  }
}
