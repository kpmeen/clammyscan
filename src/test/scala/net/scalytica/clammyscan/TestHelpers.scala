package net.scalytica.clammyscan

import java.io.File

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import org.scalatest.Matchers.fail

import scala.concurrent.Future

object TestHelpers {

  val instreamCmd = "zINSTREAM\u0000"
  val pingCmd = "zPING\u0000"
  val statusCmd = "zSTATS\u0000"
  val versionCmd = "VERSION"

  val eicarString = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-" +
    "ANTIVIRUS-TEST-FILE!$H+H*\u0000"

  val eicarStrSource = Source.single[ByteString](ByteString(eicarString))

  // scalastyle:off
  def cleanFile = file("clean.pdf")
  def eicarFile = file("eicar.com")
  def eicarTxtFile = file("eicar.com.txt")
  def eicarZipFile = file("eicarcom2.zip")

  def cleanFileSrc = fileAsSource("clean.pdf")
  def eicarFileSrc = fileAsSource("eicar.com")
  def eicarTxtFileSrc = fileAsSource("eicar.com.txt")
  def eicarZipFileSrc = fileAsSource("eicarcom2.zip")
  // scalastyle:on

  def file(fname: String): File =
    new File(this.getClass.getResource(s"/$fname").toURI)

  def fileAsSource(fname: String): Source[ByteString, Future[IOResult]] =
    FileIO.fromFile(file(fname))

  def unexpectedClamError(ce: ClamError): Nothing =
    fail(s"Unexpected ClamError result ${ce.message}")

}
