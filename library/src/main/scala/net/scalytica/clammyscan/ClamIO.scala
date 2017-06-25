package net.scalytica.clammyscan

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.Inet
import akka.io.Inet.SO.SendBufferSize
import akka.stream.scaladsl._
import akka.stream.{Materializer, StreamTcpException}
import akka.util.ByteString
import net.scalytica.clammyscan.ClamProtocol._
import net.scalytica.clammyscan.UnsignedInt._
import play.api.Logger

import scala.collection.immutable
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

/**
 * Class that provides a way of communicating with `clamd` using akka-streams.
 * It Flows through akka-tcp that sends all chunks directly to clamd. The
 * response is finally consumed by a Sink implementation that maps the end
 * value to a ScanResult.
 *
 * @param host    the host where clamd is running
 * @param port    the port number where clamd is exposed
 * @param timeout how long to wait before timing out the connection
 */
class ClamIO(
    host: String,
    port: Int,
    timeout: Duration
) {

  private val logger = Logger(this.getClass)

  private val inetAddr = new InetSocketAddress(host, port)

  /**
   * Defines the TCP socket connection to ClamAV.
   *
   * @param as ActorSystem to run the connection on.
   * @return a Future of a TCP Connection
   *         {{{Flow[ByteString, _, Future[OutgoingConnection]][ByteString]}}}
   */
  private def connection(implicit as: ActorSystem) =
    Tcp()
      .outgoingConnection(
        remoteAddress = inetAddr,
        connectTimeout = timeout,
        options = ClamIO.socketOpts
      )
      .recover {
        case err: StreamTcpException =>
          logger.debug("An error occurred trying to connect to Clam", err)
          throw ClammyException(couldNotConnect)
      }

  /*
   * Need a special flag to identify the first chunk, so we can ensure the
   * correct ClamAV command sequence can be injected in front of the stream.
   */
  private var commandInitiated: Boolean = false

  /**
   * The ClamAV protocol is very specific. This Flow aims to handle that.
   *
   * All communications must start with a specific command. Depending on the
   * command a certain sequence of bytes and chunks are expected.
   * For example, the INSTREAM command expects each chunk to come after
   * a sequence of 4 bytes with the length of the following chunk as an
   * unsigned integer.
   */
  private def stream(implicit as: ActorSystem) =
    Flow[ByteString].mapConcat { bs =>
      val builder = immutable.Seq.newBuilder[ByteString]
      if (!commandInitiated) {
        builder += Instream.cmd
        // Switch the flag indicating that command is sent
        commandInitiated = true
      }

      if (bs != StreamCompleted && !Command.isCommand(bs))
        builder += unsignedInt(bs.length)

      (builder += bs).result()
    }.concat {
      // reset the command flag
      commandInitiated = false
      // Close the stream
      Source.single(StreamCompleted)
    }

  /**
   * Sink implementation that yields a ScanResponse when it's completed.
   *
   * @param filename String with the name of the file being scanned
   * @param ec       an implicit ExecutionContext
   * @param as       an implicit ActorSystem
   * @param mat      an implicit Materializer
   * @return a `ClamSink`
   */
  private def sink(filename: String)(
      implicit ec: ExecutionContext,
      as: ActorSystem,
      mat: Materializer
  ): ClamSink = {

    case class ScanState(chunkNum: Int = 0, result: String = "") {
      def append(chunk: ByteString): ScanState = {
        copy(
          chunkNum = chunkNum + 1,
          result = s"$result${chunk.utf8String}"
        )
      }

      def validate: ScanResponse = {
        val res = result.trim
        if (ClamProtocol.okResponse.equals(res)) {
          logger.debug(s"No viruses found in $filename")
          FileOk
        } else if (ClamProtocol.unknownCommand.equals(res)) {
          logger.warn(s"Command not recognized: $res")
          ScanError(result)
        } else {
          logger.warn(s"Virus detected in $filename - $res")
          VirusFound(res)
        }
      }
    }

    Sink
      .fold[ScanState, ByteString](ScanState()) { (state, chunk) =>
        logger.debug(s"Processing new chunk ${state.chunkNum + 1}...\n")
        state.append(chunk)
      }
      .mapMaterializedValue { ss =>
        logger.debug("Materializing result...")
        ss.map(_.validate)
      }
  }

  /**
   * The method setting up a `ClamSink` complete with source and TCP connection.
   *
   * @param filename the name of the file to be scanned
   * @return a complete `ClamSink`
   */
  def scan(filename: String)(
      implicit e: ExecutionContext,
      s: ActorSystem,
      m: Materializer
  ): ClamSink = {
    logger.debug(s"Preparing to scan file $filename with clamd...")
    (stream via connection).toMat(sink(filename))(Keep.right)
  }

  // Helper for executing general commands against clamd
  private def executeCommand(command: Command)(
      implicit e: ExecutionContext,
      s: ActorSystem,
      m: Materializer
  ) =
    (Source.single(command.cmd) via connection)
      .runFold[String]("")((state, chunk) => s"$state${chunk.utf8String}")

  /**
   * Sends a PING command to clamd, expecting a PONG in response
   */
  def ping(
      implicit e: ExecutionContext,
      s: ActorSystem,
      m: Materializer
  ): Future[String] = executeCommand(Ping)

  /**
   * Ask clamd for the version string
   */
  def version(
      implicit e: ExecutionContext,
      s: ActorSystem,
      m: Materializer
  ): Future[String] = executeCommand(Version)

  /**
   * Ask clamd for its internal stats
   */
  def stats(
      implicit e: ExecutionContext,
      s: ActorSystem,
      m: Materializer
  ): Future[String] = executeCommand(Stats)

}

object ClamIO {

  val chunkSize = 262144
  val socketOpts =
    immutable.Traversable[Inet.SocketOption](SendBufferSize(chunkSize))

  /**
   * Creates a new instance of a ClamSink
   */
  def apply(host: String, port: Int, timeout: Duration)(
      implicit ec: ExecutionContext,
      as: ActorSystem,
      mat: Materializer
  ): ClamIO = new ClamIO(host, port, timeout)

  /**
   * Returns a cancelled ClamSink.
   */
  def cancelled(res: ScanResponse)(
      implicit ec: ExecutionContext,
      as: ActorSystem,
      mat: Materializer
  ): ClamSink =
    Sink.cancelled.mapMaterializedValue(_ => Future.successful(res))
}
