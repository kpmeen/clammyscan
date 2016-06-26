package net.scalytica.clammyscan

import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}
import org.scalatestplus.play._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{POST => POST_REQUEST}

//class ClammyScanSpec extends TestKit(ActorSystem("clammyscan-test-system"))
class DefaultClammyScanSpec extends PlaySpec with OneAppPerSuite {

  implicit override lazy val app =
    new GuiceApplicationBuilder()
      //      .configure(Map("ehcacheplugin" -> "disabled"))
      .build()

  //  val clammyScan = new ClammyScanParser(sys, mat, conf)

    "A ClammyScan" that {

      "is configured with default settings" should {
        "scan the upl" in {

        }
      }

    }

  //      "broadcast the file stream to both the ClamIO and save sinks" in {
  //        val filename = "eicarcom2.zip"
  //        val save = (fname: String, ctype: Option[String]) =>
  //          Sink.fold[String, ByteString]("") { (s, bs) =>
  //            s"$s${bs.utf8String}"
  //          }.mapMaterializedValue(_.map(Option.apply))
  //
  //        val ss = clammyScan.sinks[String](filename, None)(save)
  //        val comb = clammyScan.broadcastGraph(ss._1, ss._2)
  //
  //        eicarStrSource runWith comb map { res =>
  //          res._1.isLeft shouldBe true
  //          res._1.left.get match {
  //            case vf: VirusFound => succeed
  //            case ce => unexpectedClamError(ce)
  //          }
  //          res._2 shouldBe Some(eicarString)
  //        }
  //      }
  //      }

  // TODO: Test with different configuration settings.

  //  }

}
