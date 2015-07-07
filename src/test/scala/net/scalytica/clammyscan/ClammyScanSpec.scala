package net.scalytica.clammyscan

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

import org.specs2.execute._
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Left, Right}

import scala.concurrent.ExecutionContext.Implicits.global

class ClammyScanSpec extends Specification with FutureMatchers {

  "Sending the EICAR string as a stream to ClamAV" should {
    "result in clamav finding a virus" in new scanFile {
      val clamav = new ClammyScan(ClamSocket())

      val eicarEnumerator = Enumerator.fromStream(new ByteArrayInputStream(eicarString.getBytes))

      val result = (eicarEnumerator run clamav.clamScan(file)).flatMap[Result](eventuallyError => {
        eventuallyError.flatMap {
          case Left(err) => Future.successful {
            err match {
              case vf: VirusFound => success(s"Found a virus in this one... :-(")
              case ce: ClamError => failure(s"Got the excepted ClamError result")
            }
          }
          case Right(fok) => Future.successful(failure(s"Successful scan of clean file :-)"))
        }
      })
      Await.result(result, Duration(3, TimeUnit.SECONDS))
    }
  }

  "Sending a clean file as a stream to ClamAV" should {
    "result in a successful scan without errors" in new scanFile("clean.pdf") {
      val clamav = new ClammyScan(ClamSocket())

      val result = (fileEnumerator run clamav.clamScan(file)).flatMap[Result](eventuallyError => {
        eventuallyError.flatMap {
          case Left(vf) => Future.successful(failure(s"Found a virus in this one... :-("))
          case Right(fok) => Future.successful(success(s"Successful scan of clean file :-)"))
        }
      })
      Await.result(result, Duration(2, TimeUnit.MINUTES))
    }
  }

  "Sending a file stream containing the EICAR string to ClamAV" should {
    "result in a clamav finding a virus" in new scanFile("eicarcom2.zip") {
      val clamav = new ClammyScan(ClamSocket())

      val result = (fileEnumerator run clamav.clamScan(file)).flatMap[Result](eventuallyError => {
        eventuallyError.flatMap {
          case Left(err) => Future.successful {
            err match {
              case vf: VirusFound => success(s"Found a virus in this one... :-(")
              case ce: ClamError => failure(s"Got the excepted ClamError result")
            }
          }
          case Right(fok) => Future.successful(failure(s"Successful scan of clean file :-)"))
        }
      })
      Await.result(result, Duration(2, TimeUnit.MINUTES))
    }
  }

  class scanFile(fname: String = "nofile") extends Scope {
    val file = fname

    val eicarString = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*\u0000"

    val fileStream = this.getClass.getResourceAsStream(s"/$file")
    val fileEnumerator = Enumerator.fromStream(fileStream)

    fileEnumerator.onDoneEnumerating {
      fileStream.close()
    }
  }

}