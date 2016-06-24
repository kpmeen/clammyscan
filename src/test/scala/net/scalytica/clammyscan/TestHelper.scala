package net.scalytica.clammyscan

import java.io.File

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import org.scalactic.source.Position
import org.scalatest.Matchers.fail

import scala.concurrent.Future

object TestHelper {

  val instream = "zINSTREAM\u0000"
  val ping = "zPING\u0000"
  val status = "zSTATS\u0000"
  val version = "VERSION"

  val eicarString = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-" +
    "ANTIVIRUS-TEST-FILE!$H+H*\u0000"

  val eicarStrSource = Source.single[ByteString](ByteString(eicarString))

  val cleanFile = fileAsSource("clean.pdf")
  val eicarFile = fileAsSource("eicar.com")
  val eicarTxtFile = fileAsSource("eicar.com.txt")
  val eicarZipFile = fileAsSource("eicarcom2.zip")

  def fileAsSource(fname: String): Source[ByteString, Future[IOResult]] = {
    val file = new File(this.getClass.getResource(s"/$fname").toURI)
    FileIO.fromFile(file)
  }

  def unexpectedClamError(ce: ClamError)(implicit pos: Position): Nothing =
    fail(s"Unexpected ClamError result ${ce.message}")

}
