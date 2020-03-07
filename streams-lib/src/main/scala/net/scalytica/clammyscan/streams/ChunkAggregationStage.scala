package net.scalytica.clammyscan.streams

import akka.stream.stage.{
  GraphStage,
  GraphStageLogicWithLogging,
  InHandler,
  OutHandler
}
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
 * @param filename String with the name of the file being processed
 * @param chunkSize Int specifying the desired max size of each chunk
 * @param maxBytes  Int specifying the max number of bytes to process
 */
class ChunkAggregationStage(
    val filename: String,
    val chunkSize: Int,
    val maxBytes: Int
) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in: Inlet[ByteString]   = Inlet[ByteString]("Rechunking.in")
  val out: Outlet[ByteString] = Outlet[ByteString]("Rechunking.out")

  override def initialAttributes: Attributes = Attributes.name("chunker")

  override def shape = FlowShape(in, out)

  // scalastyle:off method.length
  override def createLogic(attrs: Attributes): GraphStageLogicWithLogging = {
    new GraphStageLogicWithLogging(shape) {
      private val rechunked     = ByteString.newBuilder
      private var chunks        = 0
      private var receivedBytes = 0L

      setHandlers(
        in = in,
        out = out,
        handler = new InHandler with OutHandler {
          private[this] def processChunk(chunk: ByteString): Unit = {
            receivedBytes = receivedBytes + chunk.size
            rechunked ++= chunk

            if (rechunked.isEmpty && chunkSize == chunk.size) { // pass through
              chunks = chunks + 1
              push(out, chunk)
            } else {
              if (rechunked.isEmpty && chunk.size <= chunkSize) pull(in)
              else if (rechunked.length < chunkSize) pull(in)
              else {
                val (res, next) = rechunked.result().splitAt(chunkSize)
                rechunked.clear()
                rechunked ++= next
                chunks = chunks + 1
                push(out, res)
              }
            }
          }

          override def onPush(): Unit = {
            if (receivedBytes > maxBytes) onUpstreamFinish()
            else processChunk(grab(in))
          }

          override def onPull(): Unit = pull(in)

          override def onUpstreamFinish(): Unit = {
            if (rechunked.nonEmpty) {
              val (c1, c2) = rechunked.result().splitAt(chunkSize)
              val ite      = Seq(c1, c2).filter(_.nonEmpty)
              if (ite.size > 1) emitMultiple(out, ite.iterator)
              else emit(out, ite.head)
            }

            log.debug(
              s"Finishing up chunk aggregation. Received $receivedBytes bytes" +
                s" for file $filename"
            )

            if (receivedBytes > 0L) completeStage()
            else failStage(ClammyException(CannotScanEmptyFile))
          }
        }
      )

      override def postStop(): Unit = {
        log.debug(s"ChungAggregationStage for $filename has stopped")
        rechunked.clear()
      }
    }
  }

  // scalastyle:on method.length
}
