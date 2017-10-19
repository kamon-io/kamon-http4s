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

import java.util.concurrent.Callable

import fs2.util.Attempt
import fs2.{Stream, Task}
import kamon.Kamon
import kamon.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall}
import kamon.agent.scala.KamonInstrumentation
import kamon.context.Context
import kamon.http4s.Http4s
import kamon.http4s.Metrics.ResponseMetrics._
import kamon.http4s.Metrics.{AbnormalTermination, ActiveRequests}
import kamon.trace.Span
import kamon.util.Clock
import org.http4s._

class Http4sServerInstrumentation extends KamonInstrumentation {
  forTargetType("org.http4s.server.Router$") { builder =>
    builder
      .withInterceptorFor(method("apply"), ApplyMethodInterceptor)
      .build()
  }
}

/**
  * Interceptor for org.http4s.server.Router$::apply
  */
object ApplyMethodInterceptor {
  @RuntimeType
  def around(@SuperCall callable: Callable[_]): AnyRef = {
    HttpServerServiceWrapper.wrap(callable.call())
  }
}

object HttpServerServiceWrapper {
  def wrap(obj: Any):HttpService = {

    val service = obj.asInstanceOf[HttpService]

    Service.lift { request  =>
      ActiveRequests.increment()
      val incomingContext = decodeContext(request)
      val serverSpan = Kamon.buildSpan(Http4s.generateOperationName(request))
        .asChildOf(incomingContext.get(Span.ContextKey))
        .withTag("span.kind", "server")
        .withTag("http.method", request.method.name)
        .withTag("http.url", request.uri.renderString)
        .withTag("component", "http4s-server")
        .start()

      val context = Context.create(Span.ContextKey, serverSpan)

      Kamon.withContext(context) {
        service(request)
          .attempt
          .flatMap(onFinish(serverSpan, Clock.relativeNanoTimestamp())(_).fold(Task.fail, Task.now))
      }
    }
  }

  private def onFinish(requestSpan: Span, start: Long)(r: Attempt[MaybeResponse]): Attempt[MaybeResponse] = {
    import cats.implicits._

    val elapsedTime = System.nanoTime() - start

    r.map { response =>
      val code = response.cata(_.status, Status.NotFound).code

      def capture(body: EntityBody) = body.onFinalize[Task] {
        Task.delay {
          ActiveRequests.decrement()
          if (code < 200) Responses1xx.record(elapsedTime)
          else if (code < 300) Responses2xx.record(elapsedTime)
          else if (code < 400) Responses3xx.record(elapsedTime)
          else if (code < 500) Responses4xx.record(elapsedTime)
          else Responses5xx.record(elapsedTime)

          if (isError(code))
            requestSpan.addError("error")

          if (code == StatusCodes.NotFound)
            requestSpan.setOperationName("not-found")
        }
      }.onError { cause =>
        AbnormalTermination.record(elapsedTime)
        requestSpan.addError("abnormal-termination", cause).finish()
        Stream.fail(cause)
      }
      response.cata(resp => resp.copy(body = capture(resp.body)), response)
    }.leftMap { error =>
      requestSpan.addError(error.getMessage, error).finish()
      Responses5xx.record(elapsedTime)
      error
    }
  }
}
