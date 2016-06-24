package net.scalytica.clammyscan

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.testkit.TestKit
import akka.util.ByteString
import net.scalytica.clammyscan.TestHelper._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global

class ClamIOSpec extends TestKit(ActorSystem("clamio-test-system"))
  with AsyncWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val sys = system
  implicit val mat = ActorMaterializer()

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "Using ClamIO" when {

    "the ClammySink" should {
      "return a ClamResponse" in {
        val clamIO = ClamIO()
        val sink = clamIO.sink("eicarcom2.zip")

        eicarZipFile runWith sink map { res =>
          res.isLeft shouldBe true
          res.left.get match {
            case vf: VirusFound => succeed
            case ce => unexpectedClamError(ce)
          }
        }
      }
    }

    "sending the EICAR string as a stream" should {
      "result in clamav finding a virus" in {
        eicarStrSource runWith ClamIO().scan("test-file") map { res =>
          res.isLeft shouldBe true
          res.left.get match {
            case vf: VirusFound => succeed
            case ce => unexpectedClamError(ce)
          }
        }
      }
    }

    "sending a clean file as a stream" should {
      "result in a successful scan without errors" in {
        cleanFile runWith ClamIO().scan("clean.pdf") map { res =>
          res.isRight shouldBe true
          res.right.get shouldBe FileOk()
        }
      }
    }

    "sending a zip file containing the EICAR string as a stream" should {
      "result in a virus being found" in {
        eicarZipFile runWith ClamIO().scan("eicarcom2.zip") map { res =>
          res.isLeft shouldBe true
          res.left.get match {
            case vf: VirusFound => succeed
            case ce => unexpectedClamError(ce)
          }
        }
      }
    }
  }

}