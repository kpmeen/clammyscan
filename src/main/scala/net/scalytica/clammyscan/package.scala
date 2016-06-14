package net.scalytica

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import play.api.mvc.{BodyParser, MultipartFormData}

import scala.concurrent.Future

package object clammyscan {

  type ClamResponse = Either[ClamError, FileOk]
  type MergedResponse[A] = (ClamResponse, A)
  type ClamFlow = Flow[ByteString, ClamResponse, NotUsed]
  type SaveFlow[A] = Flow[ByteString, A, NotUsed]
  type MergedFlow[A] = Flow[ByteString, (ClamResponse, A), NotUsed]

  type ClamParser[A] = BodyParser[MultipartFormData[(Future[ClamResponse], A)]]

}
