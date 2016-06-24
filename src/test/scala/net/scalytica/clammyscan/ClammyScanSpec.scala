package net.scalytica.clammyscan

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.testkit.TestKit
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}
import TestHelper._
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global

class ClammyScanSpec extends TestKit(ActorSystem("clammyscan-test-system"))
  with AsyncWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val sys = system
  implicit val mat = ActorMaterializer()

  val conf = play.api.Configuration(ConfigFactory.defaultReference())

  val clammyScan = new ClammyScanParser(sys, mat, conf)

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "Using ClammyScan" when {

    "Configured by default" should {
      "broadcast the file stream to both the ClamIO and save sinks" in {
        val filename = "eicarcom2.zip"
        val save = (fname: String, ctype: Option[String]) =>
          Sink.fold[String, ByteString]("") { (s, bs) =>
            s"$s${bs.utf8String}"
          }.mapMaterializedValue(_.map(Option.apply))

        val ss = clammyScan.sinks[String](filename, None)(save)
        val comb = clammyScan.broadcastGraph(ss._1, ss._2)

        eicarStrSource runWith comb map { res =>
          res._1.isLeft shouldBe true
          res._1.left.get match {
            case vf: VirusFound => succeed
            case ce => unexpectedClamError(ce)
          }
          res._2 shouldBe Some(eicarString)
        }
      }
    }

    // TODO: Test with different configuration settings.

  }

}
