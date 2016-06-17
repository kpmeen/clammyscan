package net.scalytica

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.api.mvc.{BodyParser, MultipartFormData}

import scala.concurrent.Future

package object clammyscan {

  type ClamResponse = Either[ClamError, FileOk]
  type TupledResponse[A] = (Future[ClamResponse], Option[A])
  type ClamSink = Sink[ByteString, Future[ClamResponse]]
  type SaveSink[A] = Sink[ByteString, Option[A]]

  type ClamParser[A] = BodyParser[MultipartFormData[TupledResponse[A]]]

}
