/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.http4s.instrumentation

import cats.data.Kleisli
import cats.effect.Effect
import cats.FlatMap
import cats.implicits._
import kamon.Kamon
import kamon.agent.libs.net.bytebuddy.asm.Advice
import kamon.agent.scala.KamonInstrumentation
import kamon.http4s.Http4s
import kamon.http4s.instrumentation.advisor.Http4sClientAdvisor
import kamon.trace.{Span, SpanCustomizer}
import org.http4s._
import org.http4s.client.DisposableResponse

class Http4sClientInstrumentation extends KamonInstrumentation {
  forTargetType("org.http4s.client.Client") { builder =>
    builder
      .withAdvisorFor(isConstructor(), classOf[Htt4sClientAdvisor2])
      .build()
  }
}

class Htt4sClientAdvisor2
object Htt4sClientAdvisor2 {
  @Advice.OnMethodExit
//  def enter[F[_] : Effect](@Advice.Argument(value = 0, readOnly = true) httpService: Kleisli[F, Request[F], DisposableResponse[F]]) = {
  def enter[F[_]](@Advice.Argument(value = 0, readOnly = false) httpService: Kleisli[F, Request[F], DisposableResponse[F]]): Kleisli[F, Request[F], DisposableResponse[F]] = {
    Kleisli { request:Request[F] =>
      val currentContext = Kamon.currentContext()
      val clientSpan = currentContext.get(Span.ContextKey)

      println("la puta qu ete pario")
      if (clientSpan.isEmpty()) httpService(request)
      else {
        val clientSpanBuilder = Kamon.buildSpan(Http4s.generateHttpClientOperationName(request))
          .asChildOf(clientSpan)
          .withMetricTag("span.kind", "client")
          .withTag("http.method", request.method.name)
          .withTag("http.url", request.uri.renderString)
          .withTag("component", "http4s.client")

        val clientRequestSpan = currentContext.get(SpanCustomizer.ContextKey)
          .customize(clientSpanBuilder)
          .start()

        val newContext = currentContext.withKey(Span.ContextKey, clientRequestSpan)
        val encodedRequest = encodeContext(newContext, request)

        println("pipipoiopipoipoipoipoioi")
        httpService(encodedRequest)
      }
    }
  }

//  @Advice.OnMethodExit
//  def exit[F[_]](@Advice.Return traveler: Kleisli[F, Request[F], Traveler[F]]): Unit = {
//  def exit[F[_]](@Advice.Enter traveler: Kleisli[F, Request[F], DisposableResponse[F]]): Kleisli[F, Request[F], DisposableResponse[F]] = {

//    println(traveler)
//    traveler
//    traveler.map{ traveler =>
//      traveler.clientRequestSpan.tag("http.status_code", traveler.disposableResponse.map(_.response.status.code))
//      if (isError(response.response.status.code))
//        traveler.clientRequestSpan.addError("error")
//      if (response.response.status.code == StatusCodes.NotFound)
//        traveler.clientRequestSpan.setOperationName("not-found")
//
//      traveler.clientRequestSpan.finish()
//      response
//    }
//  }
}


case class Traveler[F[_]:Effect](disposableResponse: F[DisposableResponse[F]], clientRequestSpan: Span)


object HttpClientWrapper {
  def wrap[F[_]:Effect](httpService: Kleisli[F, Request[F], DisposableResponse[F]]): Kleisli[F, Request[F], DisposableResponse[F]] = {
    Kleisli { request =>
      val currentContext = Kamon.currentContext()
      val clientSpan = currentContext.get(Span.ContextKey)

      if (clientSpan.isEmpty()) httpService(request)
      else {
        val clientSpanBuilder = Kamon.buildSpan(Http4s.generateHttpClientOperationName(request))
          .asChildOf(clientSpan)
          .withMetricTag("span.kind", "client")
          .withTag("http.method", request.method.name)
          .withTag("http.url", request.uri.renderString)
          .withTag("component", "http4s.client")

        val clientRequestSpan: Span = currentContext.get(SpanCustomizer.ContextKey)
          .customize(clientSpanBuilder)
          .start()

        val newContext = currentContext.withKey(Span.ContextKey, clientRequestSpan)
        val encodedRequest = encodeContext(newContext, request)

        httpService(encodedRequest).map { response =>
          clientRequestSpan.tag("http.status_code", response.response.status.code)
          if (isError(response.response.status.code))
            clientRequestSpan.addError("error")
          if (response.response.status.code == StatusCodes.NotFound)
            clientRequestSpan.setOperationName("not-found")

          clientRequestSpan.finish()
          response
        }
      }
    }
  }
}
