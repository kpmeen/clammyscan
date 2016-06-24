package net.scalytica.clammyscan

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
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

  val conf = {
    val c = ConfigFactory.defaultReference()
    new ClamConfig(play.api.Configuration(c))
  }

  val clamIO = ClamIO(conf.host, conf.port, conf.timeout)

  "Using ClamIO" when {

    "sending the EICAR string as a stream" should {
      "result in clamav finding a virus" in {
        eicarStrSource runWith clamIO.scan("test-file") map { res =>
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
        cleanFile runWith clamIO.scan("clean.pdf") map { res =>
          res.isRight shouldBe true
          res.right.get shouldBe FileOk()
        }
      }
    }

    "sending a zip file containing the EICAR string as a stream" should {
      "result in a virus being found" in {
        eicarZipFile runWith clamIO.scan("eicarcom2.zip") map { res =>
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