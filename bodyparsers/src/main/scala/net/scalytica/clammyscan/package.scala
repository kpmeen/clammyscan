package net.scalytica

import net.scalytica.clammyscan.streams.ScannedBody
import play.api.mvc.{BodyParser, MultipartFormData}

// $COVERAGE-OFF$
package object clammyscan {

  type ClamMultipart[A] = MultipartFormData[ScannedBody[A]]

  type ClamParser[A] = BodyParser[ClamMultipart[A]]

  type ChunkedClamParser[A] = BodyParser[ScannedBody[A]]

  private[clammyscan] val UnhandledException =
    "An unhandled exception was caught"

}
// $COVERAGE-ON$
