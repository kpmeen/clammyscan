//package net.scalytica.clammyscan
//
//import java.io.File
//
//import akka.actor.ActorSystem
//import akka.stream.scaladsl._
//import akka.stream.{ActorMaterializer, IOResult}
//import akka.util.ByteString
//import net.scalytica.clammyscan.TestHelper.eicarString
//import org.scalatest.{Assertion, AsyncWordSpec, BeforeAndAfterAll, Matchers}
//
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent._
//
//class ClammyScanSpec extends AsyncWordSpec
//  with Matchers
//  with BeforeAndAfterAll {
//
//  implicit val system = ActorSystem("test-system")
//  implicit val materializer = ActorMaterializer()
//
//  override def afterAll() = system.terminate() // scalastyle:ignore
//
//  def withFile(fname: String)(
//    testCode: (File, Source[ByteString, Future[IOResult]]) => Future[Assertion]
//  ): Future[Assertion] = {
//    val file = new File(this.getClass.getResource(s"/$fname").toURI)
//    val fio = FileIO.fromFile(file)
//    testCode(file, fio)
//  }
//
//  "Using ClammyScan" when {
//
//    "sending the EICAR string as a stream" should {
//      "result in clamav finding a virus" in {
//        val clamSocket = ClamSocket()
//        clamSocket.socket should not be None
//
//        val clam = new ClammyScan(clamSocket)
//        val bytes = ByteString(eicarString)
//        val source = Source.single[ByteString](bytes)
//        val clamSink = clam.sink("test-file")
//
//        source.runWith(clamSink).map { res =>
//          res.isLeft shouldBe true
//          res.left.get match {
//            case vf: VirusFound => succeed
//            case ce => fail(s"Unexpected ClamError result ${ce.message}")
//          }
//        }
//      }
//    }
//
//    "sending a clean file as a stream" should {
//      "result in a successful scan without errors" in withFile("clean.pdf") {
//        (file, source) =>
//          val clamSocket = ClamSocket()
//          clamSocket.socket should not be None
//
//          val clam = new ClammyScan(clamSocket)
//          val clamSink = clam.sink("clean.pdf")
//
//          source.runWith(clamSink).map { res =>
//            res.isRight shouldBe true
//            res.right.get shouldBe FileOk()
//          }
//      }
//    }
//
//    "sending a zip file containing the EICAR string as a stream" should {
//      "result in a virus being found" in withFile("eicarcom2.zip") {
//        (file, source) =>
//          val clamSocket = ClamSocket()
//          clamSocket.socket should not be None
//
//          val clam = new ClammyScan(clamSocket)
//          val clamSink = clam.sink("eicarcom2.zip")
//
//          source.runWith(clamSink).map { res =>
//            res.isLeft shouldBe true
//            res.left.get match {
//              case vf: VirusFound => succeed
//              case ce => fail(s"Unexpected ClamError result ${ce.message}")
//            }
//          }
//      }
//    }
//  }
//
//}