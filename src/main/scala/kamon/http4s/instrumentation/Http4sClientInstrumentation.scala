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
import fs2.Task
import kamon.Kamon
import kamon.agent.scala.KamonInstrumentation
import kamon.http4s.instrumentation.advisor.Http4sClientAdvisor
import kamon.trace.{Span, SpanCustomizer}
import org.http4s._
import org.http4s.client.DisposableResponse


class Http4sClientInstrumentation extends KamonInstrumentation {

  forTargetType("org.http4s.client.Client") { builder =>
    builder
      .withAdvisorFor(isConstructor(), classOf[Http4sClientAdvisor])
      .build()
  }
}

object HttpServiceWrapper {
  def wrap(obj: AnyRef): Kleisli[Task, Request, DisposableResponse] = {
    val httpService = obj.asInstanceOf[Kleisli[Task, Request, DisposableResponse]]

    Service.lift { request =>
      val currentContext = Kamon.currentContext()
      val clientSpan = currentContext.get(Span.ContextKey)

      if (clientSpan.isEmpty()) {
        httpService(request)
      } else {
        val clientSpanBuilder = Kamon.buildSpan(s"${request.uri.authority}")
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

        httpService(encodedRequest).map { response =>
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
