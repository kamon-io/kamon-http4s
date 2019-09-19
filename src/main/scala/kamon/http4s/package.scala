package kamon

import cats.effect.{Effect, Sync}
import kamon.context.{Context, HttpPropagation}
import org.http4s.{Header, Request, Response}
import org.slf4j.LoggerFactory
import cats.implicits._

import scala.collection.mutable

package object http4s {

  def decodeContext[F[_]:Sync](request: Request[F]): F[Context] = {
    for {
      headersTextMap <- readOnlyTextMapFromHeaders(request)
      context <- Sync[F].delay(Kamon.defaultHttpPropagation().read(headerReaderFromMap(headersTextMap)))
    } yield context
  }

  private def headerReaderFromMap(map: Map[String, String]): HttpPropagation.HeaderReader = new HttpPropagation.HeaderReader {
    override def read(header: String): Option[String] = map.get(header)
    override def readAll(): Map[String, String] = map
  }

  def encodeContext[F[_]:Effect](ctx:Context)
                                (request:Request[F]): F[Request[F]] = {
    val textMap = mutable.Map.empty[String, String]
    Kamon.defaultHttpPropagation().write(ctx, headerWriterFromMap(textMap))
    val headers = textMap.map{case (key, value) => Header(key, value)}
    Effect[F].delay(request.putHeaders(headers.toSeq: _*))
  }

  def encodeContextToResp[F[_]:Sync](ctx:Context)
                                    (response:Option[Response[F]]): F[Option[Response[F]]] = {
    val textMap = mutable.Map.empty[String, String]
    Kamon.defaultHttpPropagation().write(ctx, headerWriterFromMap(textMap))
    val headers = textMap.map{case (key, value) => Header(key, value)}
    Sync[F].delay(response.map(_.putHeaders(headers.toSeq: _*)))
  }

  def headerWriterFromMap(map: mutable.Map[String, String]): HttpPropagation.HeaderWriter = (header: String, value: String) => map.put(header, value)


  def readOnlyTextMapFromHeaders[F[_]:Sync](request: Request[F]): F[Map[String, String]] =
    Sync[F].delay(request.headers.toList.map(h => h.name.toString -> h.value).toMap)

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
