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
import kamon.context.Context
import kamon.http4s.Http4s
import kamon.http4s.instrumentation.{StatusCodes, encodeContext, isError}
import kamon.trace.Tracer.SpanBuilder
import kamon.trace.{Span, SpanCustomizer}
import org.http4s.Request
import org.http4s.client.{Client, DisposableResponse}

object ClientMiddleware {

  def apply[F[_]](client: Client[F])(implicit F:Effect[F]): Client[F] = {
    client.copy(Kleisli(serviceHandler(client.open)))
  }

  private def serviceHandler[F[_]](service: Kleisli[F, Request[F], DisposableResponse[F]])
                                  (request: Request[F])
                                  (implicit F:Effect[F]): F[DisposableResponse[F]] = {
    for {
      ctx <- F.delay(Kamon.currentContext())
      clientSpan <- F.delay(ctx.get(Span.ContextKey))
      k <- if (clientSpan.isEmpty()) service(request)
           else kamonService(service)(request)(clientSpan)(ctx)
      } yield k
  }

  private def kamonService[F[_]](service: Kleisli[F, Request[F], DisposableResponse[F]])
                                (request: Request[F])
                                (clientSpan: Span)
                                (ctx: Context)
                                (implicit F:Effect[F]): F[DisposableResponse[F]] =
    for {
      spanBuilder <- createSpanBuilder(clientSpan, ctx)(request)
      span <- createSpan(ctx, spanBuilder)
      newCtx <- newContext(ctx, span)
      encodedRequest <- encodeContext(newCtx)(request)
      response <- service(encodedRequest)
      _ <- responseHandler(span, response)
    } yield response

  private def newContext[F[_]](ctx: Context, span: Span)
                              (implicit F:Effect[F]):F[Context] =
    F.delay(ctx.withKey(Span.ContextKey, span))

  private def createSpan[F[_]](ctx: Context, spanBuilder: SpanBuilder)
                              (implicit F:Effect[F]): F[Span] =
    F.delay(ctx.get(SpanCustomizer.ContextKey).customize(spanBuilder).start())

  private def responseHandler[F[_]](span: Span, response: DisposableResponse[F])
                                   (implicit F:Effect[F]): F[Unit] = {

    val code = response.response.status.code
    handleStatusCode(span, code) *> handleError(span, code) *> handleNotfoud(span, code) *>  F.delay(span.finish())
  }

  private def handleStatusCode[F[_]](span: Span, code:Int)
                                 (implicit F: Effect[F]):F[Unit] =
    F.delay {
      if (Http4s.addHttpStatusCodeAsMetricTag) span.tagMetric("http.status_code", code.toString)
      else span.tag("http.status_code", code)
    }

  private def handleError[F[_]](span: Span, code:Int)(implicit F: Effect[F]):F[Unit] =
    F.delay(if(isError(code)) span.addError("error"))

  private def handleNotfoud[F[_]](span: Span, code:Int)(implicit F: Effect[F]):F[Unit] =
  F.delay(if(code == StatusCodes.NotFound) span.setOperationName("not-found"))

  private def createSpanBuilder[F[_]](clientSpan: Span, ctx:Context)
                                     (request: Request[F])
                                     (implicit F:Effect[F]): F[SpanBuilder] =
    for {
      operationName <- kamon.http4s.Http4s.generateHttpClientOperationName(request)
      spanBuilder <- F.delay(Kamon.buildSpan(operationName)
        .asChildOf(clientSpan)
        .withMetricTag("span.kind", "client")
        .withMetricTag("component", "http4s.client")
        .withTag("http.method", request.method.name)
        .withTag("http.url", request.uri.renderString))
    } yield spanBuilder
}
