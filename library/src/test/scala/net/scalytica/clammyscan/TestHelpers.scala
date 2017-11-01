package net.scalytica.clammyscan

import java.nio.file.Paths

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import org.scalatest.Matchers.fail

import scala.concurrent.Future

case class FileSource(
    fname: String,
    source: Source[ByteString, Future[IOResult]]
)

trait TestResources { self =>

  val eicarResult =
    Some("""{"message":"stream: Eicar-Test-Signature FOUND"}""")

  val clamdUnavailableResult =
    Some("""{"message":"Could not connect to clamd"}""")

  val eicarString = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-" +
    "ANTIVIRUS-TEST-FILE!$H+H*\u0000"

  val eicarStrSource = Source.single[ByteString](ByteString(eicarString))

  val cleanFile    = fileAsSource("clean.pdf")
  val eicarFile    = fileAsSource("eicar.com")
  val eicarTxtFile = fileAsSource("eicar.com.txt")
  val eicarZipFile = fileAsSource("eicarcom2.zip")
  val largeFile    = fileAsSource("large.zip")

  def fileAsSource(fname: String): FileSource =
    FileSource(
      fname,
      FileIO.fromPath(
        f = Paths.get(self.getClass.getResource(s"/files/$fname").toURI),
        chunkSize = 8192
      )
    )
}

object TestHelpers {

  val instreamCmd = "zINSTREAM\u0000"
  val pingCmd     = "zPING\u0000"
  val statusCmd   = "zSTATS\u0000"
  val versionCmd  = "VERSION"

  def unexpectedClamError(ce: ClamError): Nothing =
    fail(s"Unexpected ClamError result ${ce.message}")

}
