package net.scalytica.test

import java.nio.file.Paths

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import scala.concurrent.Future

case class FileSource(
    fname: String,
    source: Source[ByteString, Future[IOResult]]
)

trait TestResources {
  self =>

  val virusFoundIdentifier = "FOUND"

  val eicarResult1 =
    Some("""{"message":"stream: Eicar-Test-Signature FOUND"}""")

  val eicarResult2 =
    Some("""{"message":"stream: Win.Test.EICAR_NDB-1 FOUND"}""")

  val clamdUnavailableResult =
    Some("""{"message":"Connection to clamd caused an exception."}""")

  val eicarString =
    """X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"""

  val PingResult = s"""{"ping":"PONG"}"""

  val ExpectedVersionStr = """ClamAV \d*\.\d*\.\d*""".r

  // Akka stream Sources
  lazy val eicarStrSource = Source.single[ByteString](ByteString(eicarString))

  lazy val cleanFile    = fileAsSource("clean.pdf")
  lazy val eicarFile    = fileAsSource("eicar.com")
  lazy val eicarTxtFile = fileAsSource("eicar.com.txt")
  lazy val eicarZipFile = fileAsSource("eicarcom2.zip")
  lazy val largeFile    = fileAsSource("large.zip")
  lazy val emptyFile    = fileAsSource("empty.txt")

  def fileAsSource(fname: String): FileSource = {
    val filePath = Paths.get(self.getClass.getResource(s"/files/$fname").toURI)
    val fileSrc = FileIO.fromPath(
      f = filePath,
      chunkSize = 8192 // scalastyle:ignore
    )

    FileSource(
      fname = fname,
      source = fileSrc
    )
  }
}
