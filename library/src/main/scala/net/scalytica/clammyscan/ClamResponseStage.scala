package net.scalytica.clammyscan

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import net.scalytica.clammyscan.ClamProtocol._
import play.api.Logger

/**
 * This GraphStage handles responses from clamd. Also deals properly with the
 * corner case when clamd responds with the "INSTREAM: Size limit reached"
 * message. Clamd will immediately terminate the connection to the client after
 * sending the message. Causing a [[akka.stream.StreamTcpException]] to be
 * thrown, terminating the stream and prevents processing of the actual response
 * from clamd.
 */
class ClamResponseStage
    extends GraphStage[FlowShape[ByteString, ScanResponse]] {

  val logger = Logger(getClass)

  val in  = Inlet[ByteString]("ClamResponse.in")
  val out = Outlet[ScanResponse]("ClamResponse.out")

  override def shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes) = {

    new GraphStageLogic(shape) {

      case class ScanState(result: String = "") {
        def append(chunk: ByteString): ScanState = {
          copy(s"$result${chunk.utf8String}")
        }

        def validate: ScanResponse = ScanResponse.fromString(result.trim)
      }

      private var state = ScanState()

      setHandlers(
        in = in,
        out = out,
        handler = new InHandler with OutHandler {

          override def onPush(): Unit = {
            val c = grab(in)
            state = state.append(c)
          }

          override def onPull(): Unit = pull(in)

          override def onUpstreamFinish(): Unit = {
            emit(out, state.validate)
            completeStage()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            state.validate match {
              case ScanError(msg) if msg == IncompleteResponse =>
                super.onUpstreamFailure(ex)

              case res =>
                emit(out, res)
                completeStage()
            }
          }

        }
      )

      override def postStop(): Unit = state = ScanState()
    }
  }

}
