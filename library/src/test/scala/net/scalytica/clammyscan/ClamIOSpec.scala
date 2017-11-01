package net.scalytica.clammyscan

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ClamIOSpec
    extends TestKit(ActorSystem("clamio-test-system"))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with TestResources {

  implicit val sys = system
  implicit val mat = ActorMaterializer()

  override def afterAll(): Unit = {
    mat.shutdown()
    TestKit.shutdownActorSystem(system)
  }

  val conf = {
    val c = ConfigFactory.defaultReference()
    new ClamConfig(play.api.Configuration(c))
  }

  val clamIO = ClamIO(conf.host, conf.port, conf.timeout, conf.streamMaxLength)

  "A ClamIO" which {

    "receives the EICAR string as a stream" should {
      "result in clamav finding a virus" in {
        val res = Await.result(
          eicarStrSource runWith clamIO.scan("test-file"),
          10 seconds
        )
        res shouldBe a[VirusFound]
      }
    }

    "receives a clean file as a stream" should {
      "result in a successful scan without errors" in {
        val res = Await.result(
          cleanFile.source runWith clamIO.scan(cleanFile.fname),
          10 seconds
        )
        res shouldBe FileOk
      }
    }

    "receives a zip file containing the EICAR string as a stream" should {
      "result in a virus being found" in {
        val res = Await.result(
          eicarZipFile.source runWith clamIO.scan(eicarZipFile.fname),
          10 seconds
        )
        res shouldBe a[VirusFound]
      }
    }

    "receives a large file as a stream" should {
      "result in a scan error" in {
        val res = Await.result(
          largeFile.source runWith clamIO.scan(largeFile.fname),
          10 seconds
        )
        res shouldBe a[ScanError]
      }
    }

    "receives a PING command" should {
      "result in a PONG" in {
        Await.result(clamIO.ping, 10 seconds) === "PONG"
      }
    }

    "receives a VERSION command" should {
      "result in the clamd version string" in {
        Await.result(clamIO.version, 10 seconds) should startWith("ClamAV")
      }
    }

    "receives a STATUS command" should {
      "result in the clamd status message" in {
        val res = Await.result(clamIO.stats, 10 seconds)
        res should include("POOLS")
        res should include("STATE: VALID PRIMARY")
        res should include("THREADS")
        res should include("QUEUE")
        res should include("MEMSTATS")
        res should include("END")
      }
    }
  }

}
