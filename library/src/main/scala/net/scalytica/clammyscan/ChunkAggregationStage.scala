package net.scalytica.clammyscan

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

/**
 * GraphStage that re-arranges incoming chunks into chunks of the specified
 * {{{chunkSize}}}. It will aggregate bytes until enough elements have been
 * processed to emit a new chunk of the correct size. The last chunk may be
 * smaller than {{{chunkSize}}}.
 *
 * When the stage has processed the {{{maxBytes}}} number of bytes, the file
 * stream is too large, and the stage will signal downstream that it has
 * completed. This allows downstream to complete and capture the expected
 * response from clamd.
 *
 * @param chunkSize Int specifying the desired max size of each chunk
 * @param maxBytes  Int specifying the max number of bytes to process
 */
class ChunkAggregationStage(
    val chunkSize: Int,
    val maxBytes: Int
) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in  = Inlet[ByteString]("Rechunking.in")
  val out = Outlet[ByteString]("Rechunking.out")

  override def initialAttributes: Attributes = Attributes.name("chunker")

  override def shape = FlowShape(in, out)

  // scalastyle:off method.length
  override def createLogic(inheritedAttributes: Attributes) = {

    new GraphStageLogic(shape) {

      private val rechunked     = ByteString.newBuilder
      private var chunks        = 0
      private var receivedBytes = 0L

      setHandlers(
        in = in,
        out = out,
        handler = new InHandler with OutHandler {

          override def onPush(): Unit = {
            if (receivedBytes > maxBytes) {
              onUpstreamFinish()
            } else {
              val chunk = grab(in)

              receivedBytes = receivedBytes + chunk.size
              rechunked ++= chunk

              if (rechunked.length < chunkSize) {
                pull(in)
              } else {
                val (res, next) = rechunked.result().splitAt(maxBytes)
                rechunked.clear()
                rechunked ++= next
                chunks = chunks + 1
                push(out, res)
              }
            }
          }

          override def onPull(): Unit = pull(in)

          override def onUpstreamFinish(): Unit = {
            val result = rechunked.result()
            if (result.nonEmpty) emit(out, result)
            completeStage()
          }

        }
      )

      override def postStop(): Unit = rechunked.clear()
    }
  }

  // scalastyle:on method.length

}
