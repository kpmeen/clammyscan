package net.scalytica.clammyscan.streams

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.Inet.SO.SendBufferSize
import akka.stream._
import akka.stream.scaladsl.{Tcp, _}
import akka.util.ByteString
import net.scalytica.clammyscan.streams.ClamProtocol._
import net.scalytica.clammyscan.streams.UnsignedInt._

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Class that provides a way of communicating with `clamd` using akka-streams.
 * It Flows through akka-tcp that sends all chunks directly to clamd. The
 * response is finally consumed by a Sink implementation that maps the end
 * value to a ScanResult.
 *
 * @param host     the host where clamd is running
 * @param port     the port number where clamd is exposed
 * @param timeout  how long to wait before timing out the connection
 * @param maxBytes the max number of bytes clamd is configured to accept.
 * @see [[ClamConfig]]
 */
class ClamIO(
    host: String,
    port: Int,
    timeout: Duration,
    maxBytes: Int
) {

  private[this] val inetAddr = new InetSocketAddress(host, port)

  /**
   * Defines the TCP socket connection to ClamAV.
   *
   * @param as ActorSystem to run the connection on.
   * @return a Future of a TCP Connection Flow
   */
  private[this] def connection(
      implicit as: ActorSystem
  ): Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] =
    Tcp()
      .outgoingConnection(
        remoteAddress = inetAddr,
        connectTimeout = timeout,
        options = Vector(SendBufferSize(ClamIO.MaxChunkSize))
      )
      .recover {
        case _: StreamTcpException =>
          throw ClammyException(CouldNotConnect)
      }

  /**
   * Flow that builds chunks of expected size from the incoming elements. Also
   * it will stop consuming chunks once the {{{maxBytes}}} limit is reached to
   * ensure that upstream doesn't push new chunks to an already closed socket.
   * Not doing so is very likely to trigger a race condition where a
   * [[StreamTcpException]] is thrown before the [[MaxSizeExceededResponse]]
   * message is received from clamd.
   * Since downstream will cancel after the first chunk exceeding the max size
   * is received, this implementation is good enough, and will not process
   * chunks needlessly.
   */
  private[this] def chunker =
    Flow.fromGraph(new ChunkAggregationStage(ClamIO.MaxChunkSize, maxBytes))

  /**
   * The ClamAV protocol is very specific. This Flow aims to handle that.
   *
   * All communications must start with a specific command. Depending on the
   * command a certain sequence of bytes and chunks are expected.
   * For example, the INSTREAM command expects each chunk to come after
   * a sequence of 4 bytes with the length of the following chunk as an
   * unsigned integer.
   */
  private[this] def stream =
    Flow[ByteString]
      .statefulMapConcat { () =>
        var commandInitiated: Boolean = false
        bs =>
          val builder = immutable.Seq.newBuilder[ByteString]
          // If this is the first ByteString we need to prefix with the Instream
          // command to tell clamd that we're going to start a new scan.
          if (!commandInitiated) {
            commandInitiated = true
            builder += Instream.cmd
          }

          // If the current chunk is not a command and not the StreamCompleted
          // chunk we append the size of the next chunk as specified in the
          // clamd docs.
          if (bs != StreamCompleted && !Command.isCommand(bs))
            builder += unsignedInt(bs.length)

          (builder += bs).result()
      }
      .concat {
        // Append the stream completed bytes to tell clamd the end is reached.
        Source.single(StreamCompleted)
      }

  /**
   * Sink implementation that yields a ScanResponse when it's completed.
   *
   * @return a `ClamSink`
   */
  private[this] def sink: ClamSink =
    Flow.fromGraph(new ClamResponseStage).toMat(Sink.head)(Keep.right)

  // Helper for executing general commands against clamd
  private[this] def execClamCommand(command: Command)(
      implicit s: ActorSystem,
      m: Materializer
  ) =
    (Source.single(command.cmd) via connection)
      .runFold[String]("")((state, chunk) => s"$state${chunk.utf8String}")

  /**
   * The method setting up a `ClamSink` complete with source and TCP connection.
   *
   * @param filename the name of the file to be scanned
   * @return a complete `ClamSink`
   */
  def scan(filename: String)(implicit s: ActorSystem): ClamSink =
    (chunker via stream via connection).toMat(sink)(Keep.right)

  /**
   * Sends a PING command to clamd, expecting a PONG in response
   */
  def ping(implicit s: ActorSystem, m: Materializer): Future[String] =
    execClamCommand(Ping)

  /**
   * Ask clamd for the version string
   */
  def version(implicit s: ActorSystem, m: Materializer): Future[String] =
    execClamCommand(Version)

  /**
   * Ask clamd for its internal stats
   */
  def stats(
      implicit s: ActorSystem,
      m: Materializer
  ): Future[String] = execClamCommand(Stats)

}

object ClamIO {

  val MaxChunkSize: Int = 262144

  def apply(
      host: String,
      port: Int,
      timeout: Duration,
      maxBytes: Int
  ): ClamIO =
    new ClamIO(host, port, timeout, maxBytes)

  def apply(config: ClamConfig): ClamIO = {
    new ClamIO(
      host = config.host,
      port = config.port,
      timeout = config.timeout,
      maxBytes = config.streamMaxLength
    )
  }

  /**
   * Returns a cancelled ClamSink.
   */
  def cancelled(res: ScanResponse): ClamSink =
    Sink.cancelled.mapMaterializedValue(_ => Future.successful(res))
}
