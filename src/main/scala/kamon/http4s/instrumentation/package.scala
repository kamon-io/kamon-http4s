/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.http4s

import cats.effect.Sync
import kamon.Kamon
import kamon.context.{Context, TextMap}
import org.http4s.{Header, Request}
import cats.implicits._
import org.slf4j.LoggerFactory

package object instrumentation {

  def decodeContext[F[_]:Sync](request: Request[F]): F[Context] = {
    for {
      headersTextMap <- readOnlyTextMapFromHeaders(request)
      context <- Sync[F].delay(Kamon.contextCodec().HttpHeaders.decode(headersTextMap))
    } yield context
  }

  def encodeContext[F[_]:Sync](ctx:Context, request:Request[F]): Request[F] = {
    val textMap = Kamon.contextCodec().HttpHeaders.encode(ctx)
    val headers = textMap.values.map{case (key, value) => Header(key, value)}
    request.putHeaders(headers.toSeq: _*)
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


