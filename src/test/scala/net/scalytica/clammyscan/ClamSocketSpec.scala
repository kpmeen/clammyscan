//package net.scalytica.clammyscan
//
//import akka.actor.ActorSystem
//import akka.stream.ActorMaterializer
//import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
//
//import scala.concurrent.ExecutionContext.Implicits.global
//
//class ClamSocketSpec extends WordSpec
//  with Matchers
//  with BeforeAndAfterAll {
//
//  implicit val system = ActorSystem("socket-test")
//  implicit val materializer = ActorMaterializer()
//
//  override def afterAll() = system.terminate() // scalastyle:ignore
//
//  "Using a ClamSocket" when {
//
//    "initializing" should {
//      "open a new socket to clamd" in {
//        val clamSocket = ClamSocket()
//        clamSocket.isConnected shouldBe true
//      }
//    }
//
//    "terminating" should {
//      "close the socket" in {
//        val clamSocket = ClamSocket()
//        clamSocket.isConnected shouldBe true
//
//        clamSocket.terminate()
//
//        clamSocket.isConnected shouldBe false
//      }
//    }
//
//  }
//
//}
