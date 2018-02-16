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


package kamon.http4s.middleware.client

import cats.data.Kleisli
import cats.effect.Effect
import cats.implicits._

import kamon.Kamon
import kamon.http4s.Http4s
import kamon.http4s.instrumentation.{StatusCodes, encodeContext, isError}
import kamon.trace.{Span, SpanCustomizer}

import org.http4s.Request
import org.http4s.client.{Client, DisposableResponse}

object ClientMiddleware {
  def apply[F[_]:Effect](client: Client[F]): Client[F] = {
    def wrap(httpService: Kleisli[F, Request[F], DisposableResponse[F]]): Kleisli[F, Request[F], DisposableResponse[F]] =
      Kleisli { request =>
        val currentContext = Kamon.currentContext()
        val clientSpan = currentContext.get(Span.ContextKey)

        if (clientSpan.isEmpty()) httpService(request)
        else {
          val clientSpanBuilder = Kamon.buildSpan(Http4s.generateHttpClientOperationName(request))
            .asChildOf(clientSpan)
            .withMetricTag("span.kind", "client")
            .withMetricTag("component", "http4s.client")
            .withTag("http.method", request.method.name)
            .withTag("http.url", request.uri.renderString)

          val clientRequestSpan: Span = currentContext.get(SpanCustomizer.ContextKey)
            .customize(clientSpanBuilder)
            .start()

          val newContext = currentContext.withKey(Span.ContextKey, clientRequestSpan)
          val encodedRequest = encodeContext(newContext, request)

          httpService(encodedRequest).map { response =>

            if (Http4s.addHttpStatusCodeAsMetricTag) {
              clientRequestSpan.tagMetric("http.status_code", response.response.status.code.toString)
            } else {
              clientRequestSpan.tag("http.status_code", response.response.status.code)
            }

            if (isError(response.response.status.code))
              clientRequestSpan.addError("error")
            if (response.response.status.code == StatusCodes.NotFound)
              clientRequestSpan.setOperationName("not-found")

            clientRequestSpan.finish()
            response
          }
        }
      }

    client.copy(open = wrap(client.open))
  }
}
