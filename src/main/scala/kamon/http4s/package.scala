package kamon

import cats.effect.{Effect, Sync}
import kamon.context.{Context, TextMap}
import org.http4s.{Header, Request}
import org.slf4j.LoggerFactory
import cats.implicits._

package object http4s {

  def decodeContext[F[_]:Sync](request: Request[F]): F[Context] = {
    for {
      headersTextMap <- readOnlyTextMapFromHeaders(request)
      context <- Sync[F].delay(Kamon.contextCodec().HttpHeaders.decode(headersTextMap))
    } yield context
  }

  def encodeContext[F[_]:Effect](ctx:Context)
                                (request:Request[F]): F[Request[F]] = {
    val textMap = Kamon.contextCodec().HttpHeaders.encode(ctx)
    val headers = textMap.values.map{case (key, value) => Header(key, value)}
    Effect[F].delay(request.putHeaders(headers.toSeq: _*))
  }

  def readOnlyTextMapFromHeaders[F[_]:Sync](request: Request[F]): F[TextMap] = Sync[F].delay(new TextMap {
    private val headersMap = request.headers.map(h => h.name.toString -> h.value).toMap

    override def values: Iterator[(String, String)] = headersMap.iterator
    override def get(key: String): Option[String] = headersMap.get(key)
    override def put(key: String, value: String): Unit = {}
  })

  def isError(statusCode: Int): Boolean =
    statusCode >= 500 && statusCode < 600

  object StatusCodes {
    val NotFound = 404
  }

  trait Log[F[_]] {
    def info(msg: String): F[Unit]
    def warn(msg: String): F[Unit]
    def debug(msg: String): F[Unit]
    def error(error: Throwable): F[Unit]
  }

  object Log {
    private val logger = LoggerFactory.getLogger(this.getClass)

    implicit def syncLogInstance[F[_]](implicit F: Sync[F]): Log[F] =
      new Log[F] {
        override def info(msg: String): F[Unit] = F.delay(logger.info(msg))
        override def warn(msg: String): F[Unit] = F.delay(logger.warn(msg))
        override def debug(msg: String): F[Unit] = F.delay(logger.debug(msg))
        override def error(error: Throwable): F[Unit] = F.delay(logger.error(error.getMessage, error))
      }
  }
}
