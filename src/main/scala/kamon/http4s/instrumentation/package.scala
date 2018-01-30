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

import kamon.Kamon
import kamon.context.{Context, TextMap}
import org.http4s.{Header, Request}

package object instrumentation {

  def decodeContext[F[_]](request: Request[F]): Context = {
    val headersTextMap = readOnlyTextMapFromHeaders(request)
    Kamon.contextCodec().HttpHeaders.decode(headersTextMap)
  }

  def encodeContext[F[_]](ctx:Context, request:Request[F]): Request[F] = {
    val textMap = Kamon.contextCodec().HttpHeaders.encode(ctx)
    val headers = textMap.values.map{case (key, value) => Header(key, value)}
    request//.putHeaders(headers.toSeq: _*)
  }

  private def readOnlyTextMapFromHeaders[F[_]](request: Request[F]): TextMap = new TextMap {
    private val headersMap = request.headers.map(h => h.name.toString -> h.value).toMap

    override def values: Iterator[(String, String)] = headersMap.iterator
    override def get(key: String): Option[String] = headersMap.get(key)
    override def put(key: String, value: String): Unit = {}
  }

  def isError(statusCode: Int): Boolean =
    statusCode >= 500 && statusCode < 600

  object StatusCodes {
    val NotFound = 404
  }
}
