package net.scalytica.clammyscan.streams

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import net.scalytica.clammyscan.streams.ClamProtocol.MaxSizeExceededResponse
import net.scalytica.test.{FlakyTests, TestResources}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.tagobjects.Retryable

import scala.concurrent.Await
import scala.concurrent.duration._

class ClamIOSpec
    extends TestKit(ActorSystem("clamio-test-system"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with FlakyTests
    with TestResources {

  implicit val sys: ActorSystem  = system
  implicit val mat: Materializer = Materializer(sys)

  override def afterAll(): Unit = {
    mat.shutdown()
    TestKit.shutdownActorSystem(system)
  }

  val akkaLoggingConf = {
    // Using Java Map instead of CollectionConverters to ensure compatibility
    // with Scala 2.12
    val m = new java.util.HashMap[String, Any]()
    m.put("akka.loggers", """["akka.event.slf4j.Slf4jLogger"]""")
    m.put("akka.loglevel", "DEBUG")
    m.put("akka.logging-filter", "akka.event.slf4j.Slf4jLoggingFilter")
    m
  }

  val conf = {
    val c = ConfigFactory
      .defaultReference()
      .withFallback(ConfigFactory.parseMap(akkaLoggingConf))
      .withFallback(sys.settings.config)
      .resolve()
    new ClamConfig(c)
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
      "result in a scan error" taggedAs Retryable in {
        val res =
          Await.result(
            largeFile.source runWith clamIO.scan(largeFile.fname),
            10 seconds
          )

        res match {
          case ScanError(msg) => msg should include(MaxSizeExceededResponse)

          case bad => fail(s"Expected ScanError, found ${bad.getClass}")
        }
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
