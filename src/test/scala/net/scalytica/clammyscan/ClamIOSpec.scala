package net.scalytica.clammyscan

import java.io.File

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, IOResult}
import akka.testkit.TestKit
import akka.util.ByteString
import net.scalytica.clammyscan.TestHelper.eicarString
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

class ClamIOSpec extends TestKit(ActorSystem("test-system"))
  with AsyncWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val mat = ActorMaterializer()

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  def withFile(fname: String)(
    testCode: (Source[ByteString, Future[IOResult]]) => Future[Assertion]
  ): Future[Assertion] = {
    val file = new File(this.getClass.getResource(s"/$fname").toURI)
    val fio = FileIO.fromFile(file)
    testCode(fio)
  }

  "Using ClammyScan" when {

    "sending the EICAR string as a stream" should {
      "result in clamav finding a virus" in {
        val bytes = ByteString(eicarString)
        val source = Source.single[ByteString](bytes)

        source runWith ClamIO().scan("test-file") map { res =>
          res.isLeft shouldBe true
          res.left.get match {
            case vf: VirusFound => succeed
            case ce => fail(s"Unexpected ClamError result ${ce.message}")
          }
        }
      }
    }

    "sending a clean file as a stream" should {
      "result in a successful scan without errors" in withFile("clean.pdf") {
        (source) =>
          source runWith ClamIO().scan("clean.pdf") map { res =>
            res.isRight shouldBe true
            res.right.get shouldBe FileOk()
          }
      }
    }

    "sending a zip file containing the EICAR string as a stream" should {
      "result in a virus being found" in withFile("eicarcom2.zip") {
        (source) =>
          source runWith ClamIO().scan("eicarcom2.zip") map { res =>
            res.isLeft shouldBe true
            res.left.get match {
              case vf: VirusFound => succeed
              case ce => fail(s"Unexpected ClamError result ${ce.message}")
            }
          }
      }
    }
  }

}