package net.scalytica.clammyscan

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

import org.specs2.execute._
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.libs.iteratee._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Left, Right}

class ClammyScanSpec extends Specification with FutureMatchers {

  "Sending a clean file as a stream to ClamAV" should {
    "result in a successful scan without errors" in {
      val clamav = new ClammyScan

      val cleanArchiveStream = this.getClass.getResourceAsStream("/clean.pdf")
      val fileEnumerator = Enumerator.fromStream(input = cleanArchiveStream)

      val r = fileEnumerator run clamav.clamScan()

      fileEnumerator.onDoneEnumerating {
        cleanArchiveStream.close()
      }

      val result = r.flatMap[Result] {
        case Left(vf) => Future.successful(failure(s"Found a virus in this one... :-("))
        case Right(fok) => Future.successful(success(s"Successful scan of clean file :-)"))
      }

      Await.result(result, Duration(2, TimeUnit.MINUTES))
    }
  }

  //  "Sending file as a stream to ClamAV that is larger than the allowed max stream size" should {
  //    "result in a ClamError" in {
  //      val clamav = new ClammyScan
  //
  //      val cleanArchiveStream = this.getClass.getResourceAsStream("/some-huge.zip")
  //      val fileEnumerator = Enumerator.fromStream(input = cleanArchiveStream)
  //
  //      fileEnumerator.onDoneEnumerating {
  //        cleanArchiveStream.close()
  //      }
  //      val r = fileEnumerator run clamav.clamScan()
  //
  //
  //      val result = r.flatMap[Result] {
  //        case Left(err) => Future.successful {
  //          err match {
  //            case vf: VirusFound => failure(s"Found a virus in this one... :-(")
  //            case ce: ClamError => success(s"Got the excepted ClamError result")
  //          }
  //        }
  //        case Right(fok) => Future.successful(failure(s"Successful scan of clean file :-)"))
  //      }
  //
  //      Await.result(result, Duration(2, TimeUnit.MINUTES))
  //    }
  //  }

  "Sending the EICAR string as a stream to ClamAV" should {
    "result in clamav finding a virus" in {
      val clamav = new ClammyScan

      val eicarString = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*\0"
      val eicarEnumerator = Enumerator.fromStream(new ByteArrayInputStream(eicarString.getBytes))

      val r = eicarEnumerator run clamav.clamScan()

      val result = r.flatMap[Result] {
        case Left(err) => Future.successful {
          err match {
            case vf: VirusFound => success(s"Found a virus in this one... :-(")
            case ce: ClamError => failure(s"Got the excepted ClamError result")
          }
        }
        case Right(fok) => Future.successful(failure(s"Successful scan of clean file :-)"))
      }

      Await.result(result, Duration(3, TimeUnit.SECONDS))
    }
  }

  "Sending a file stream containing the EICAR string to ClamAV" should {
    "result in a clamav finding a virus" in {
      val clamav = new ClammyScan

      val eicarArchiveStream = this.getClass.getResourceAsStream("/eicarcom2.zip")
      val infectedEnumerator = Enumerator.fromStream(input = eicarArchiveStream)

      val r = infectedEnumerator run clamav.clamScan()

      infectedEnumerator.onDoneEnumerating {
        eicarArchiveStream.close()
      }

      val result = r.flatMap[Result] {
        case Left(err) => Future.successful {
          err match {
            case vf: VirusFound => success(s"Found a virus in this one... :-(")
            case ce: ClamError => failure(s"Got the excepted ClamError result")
          }
        }
        case Right(fok) => Future.successful(failure(s"Successful scan of clean file :-)"))
      }

      Await.result(result, Duration(2, TimeUnit.MINUTES))
    }
  }

}