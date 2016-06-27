package net.scalytica.clammyscan

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import net.scalytica.clammyscan.TestHelpers._
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ClamIOSpec extends TestKit(ActorSystem("clamio-test-system"))
  with WordSpecLike
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
        val res = Await.result(
          eicarStrSource runWith clamIO.scan("test-file"),
          10 seconds
        )
        res.isLeft shouldBe true
        res.left.get match {
          case vf: VirusFound => Succeeded
          case ce => unexpectedClamError(ce)
        }

      }
    }

    "sending a clean file as a stream" should {
      "result in a successful scan without errors" in {
        val res = Await.result(
          cleanFileSrc runWith clamIO.scan("clean.pdf"),
          10 seconds
        )

        res.isRight shouldBe true
        res.right.get shouldBe FileOk()
      }
    }

    "sending a zip file containing the EICAR string as a stream" should {
      "result in a virus being found" in {
        val res = Await.result(
          eicarZipFileSrc runWith clamIO.scan("eicarcom2.zip"),
          10 seconds
        )

        res.isLeft shouldBe true
        res.left.get match {
          case vf: VirusFound => Succeeded
          case ce => unexpectedClamError(ce)
        }
      }
    }

  }

}