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
 *
 * @param host
 * @param port
 * @param timeout
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
   * @param as
   * @return
   */
  def connection(implicit as: ActorSystem) = // scalastyle:ignore
    Tcp().outgoingConnection(
      remoteAddress = inetAddr,
      connectTimeout = timeout,
      options = ClamIO.socketOpts
    ).recover {
      case err: StreamTcpException =>
        logger.debug("An error occurred trying to connect to Clam", err)
        throw ClammyException(couldNotConnect)
    }

  /*
   * Need a special flag to identify the first chunk, so we can ensure the
   * correct ClamAV command sequence can be injected infront of the stream.
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
  def enrich(implicit as: ActorSystem) = // scalastyle:ignore
    Flow[ByteString].mapConcat { bs =>
      val builder = immutable.Seq.newBuilder[ByteString]
      if (!commandInitiated) {
        builder += Instream.cmd
        // Switch the flag indicating that command is sent
        commandInitiated = true
      }

      if (bs != StreamCompleted && !ByteStrCommand.isCommand(bs))
        builder += unsignedInt(bs.length)

      (builder += bs).result()
    }.concat {
      // reset the command flag
      commandInitiated = false
      // Close the stream
      Source.single(StreamCompleted)
    }

  /**
   *
   * @param filename
   * @return
   */
  def sink(filename: String)(
    implicit
    ec: ExecutionContext,
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

      def validate: ClamResponse = {
        val res = result.trim
        if (ClamProtocol.okResponse.equals(res)) {
          logger.debug(s"No viruses found in $filename")
          Right(FileOk())
        } else if (ClamProtocol.unknownCommand.equals(res)) {
          logger.warn(s"Command not recognized: $res")
          Left(ScanError(result))
        } else {
          logger.warn(s"Virus detected in $filename - $res")
          Left(VirusFound(res))
        }
      }
    }

    Sink.fold[ScanState, ByteString](ScanState()) { (state, chunk) =>
      logger.debug(s"Processing new chunk ${state.chunkNum + 1}...\n")
      state.append(chunk)
    }.mapMaterializedValue { ss =>
      logger.debug("Materializing result...")
      ss.map(_.validate)
    }
  }

  /**
   *
   * @param filename
   * @return
   */
  def scan(filename: String)(
    implicit
    ec: ExecutionContext,
    system: ActorSystem,
    mat: Materializer
  ): ClamSink = {
    logger.debug(s"Preparing to scan file $filename with clamd...")
    (enrich via connection).toMat(sink(filename))(Keep.right)
  }

}

object ClamIO {

  val chunkSize = 262144
  val socketOpts = immutable.Traversable[Inet.SocketOption](
    SendBufferSize(chunkSize)
  )

  def apply(host: String, port: Int, timeout: Duration)(
    implicit
    ec: ExecutionContext,
    as: ActorSystem,
    mat: Materializer
  ): ClamIO = new ClamIO(host, port, timeout)

  /**
   * Returns a cancelled ClamSink.
   */
  def cancelled(res: ClamResponse)(
    implicit
    ec: ExecutionContext,
    as: ActorSystem,
    mat: Materializer
  ): ClamSink = Sink.cancelled.mapMaterializedValue(_ => Future.successful(res))
}