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

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import kamon.Kamon
import kamon.agent.scala.KamonInstrumentation
import kamon.context.Context
import kamon.http4s.Metrics.{GeneralMetrics, RequestTimeMetrics, ResponseTimeMetrics, ServiceMetrics}
import kamon.http4s.instrumentation.advisor.Http4sServerAdvisor
import kamon.metric.{Histogram, RangeSampler}
import kamon.trace.Span
import org.http4s._


class Http4sServerInstrumentation extends KamonInstrumentation {
  forTargetType("org.http4s.server.Router$") { builder =>
    builder
      .withAdvisorFor(method("apply"), classOf[Http4sServerAdvisor])
      .build()
  }
}

object HttpServerServiceWrapper {
  def apply[F[_]:Sync](service: HttpService[F]):HttpService[F] = {
    val serviceMetrics = ServiceMetrics(GeneralMetrics(), RequestTimeMetrics(), ResponseTimeMetrics())
    Kleisli(metricsService[F](serviceMetrics, service)(_))
  }

  private def metricsService[F[_]: Sync](serviceMetrics: ServiceMetrics, service: HttpService[F])(req: Request[F]): OptionT[F, Response[F]] = OptionT {
    for {
      now <- Sync[F].delay(System.nanoTime())
      serverSpan <- createSpan(req)
      _ <- Sync[F].delay(serviceMetrics.generalMetrics.activeRequests.increment())
      e <- Kamon.withContext(Context.create(Span.ContextKey, serverSpan))(service(req).value.attempt)
      resp <- metricsServiceHandler(req.method, now, serviceMetrics, serverSpan, e)
    } yield resp
  }

  private def metricsServiceHandler[F[_]: Sync](method: Method,
                                                start: Long,
                                                serviceMetrics: ServiceMetrics,
                                                serverSpan: Span,
                                                e: Either[Throwable, Option[Response[F]]]): F[Option[Response[F]]] = {
    for {
      elapsed <- EitherT.liftF[F, Throwable, Long](Sync[F].delay(System.nanoTime() - start))
      respOpt <- EitherT(e.bitraverse[F, Throwable, Option[Response[F]]](
        manageServiceErrors(method, elapsed, serviceMetrics).as(_),
        _.map(manageResponse(method, start, elapsed, serviceMetrics, serverSpan)).pure[F]
        ))
    } yield respOpt
  }.fold(
    Sync[F].raiseError[Option[Response[F]]],_.fold(handleUnmatched(serviceMetrics))(handleMatched)
  ).flatten


  private def manageResponse[F[_]: Sync](m: Method,
                                         start: Long,
                                         elapsedInit: Long,
                                         serviceMetrics: ServiceMetrics,
                                         spanServer:Span)(response: Response[F]): Response[F] = {
    val newBody = response.body
      .onFinalize {
        for {
          elapsed <- Sync[F].delay(System.nanoTime() - start)
          _ <- incrementCounts(serviceMetrics.generalMetrics.headersTimes, elapsedInit)
          _ <- requestMetrics(serviceMetrics.requestTimeMetrics, serviceMetrics.generalMetrics.activeRequests)(m, elapsed)
          _ <- responseMetrics(serviceMetrics.responseTimeMetrics, response.status, elapsed)
          _ <- finishSpan(spanServer, response.status, elapsed)
        } yield ()
      }
      .handleErrorWith(
        e =>
          Stream.eval(
            incrementCounts(serviceMetrics.generalMetrics.abnormalTerminations, elapsedInit)) *>
            Stream.raiseError[Byte](e))
    response.copy(body = newBody)
  }

  private def createSpan[F[_]: Sync](request: Request[F]): F[Span] = {
    Sync[F].delay {
      val incomingContext = decodeContext(request)
      val serverSpan = Kamon.buildSpan(kamon.http4s.Http4s.generateOperationName(request))
        .asChildOf(incomingContext.get(Span.ContextKey))
        .withMetricTag("span.kind", "server")
        .withTag("http.method", request.method.name)
        .withTag("http.url", request.uri.renderString)
        .withTag("component", "http4s.server")
        .start()
      serverSpan
    }
  }

  private def finishSpan[F[_]: Sync](serverSpan:Span, status: Status, elapsed: Long): F[Unit] =
    Sync[F].delay {
      val endTimestamp = Kamon.clock().instant()
      serverSpan.tag("http.status_code", status.code)

      if (status.code < 500) {
        if (status.code == StatusCodes.NotFound)
          serverSpan.setOperationName("not-found")
      } else {
        serverSpan.addError("error")
      }

      serverSpan.finish(endTimestamp)
    }

  private def manageServiceErrors[F[_]: Sync](m: Method, elapsed: Long, serviceMetrics: ServiceMetrics): F[Unit] =
    requestMetrics(serviceMetrics.requestTimeMetrics, serviceMetrics.generalMetrics.activeRequests)(m,elapsed) *>
      incrementCounts(serviceMetrics.generalMetrics.serviceErrors, elapsed)

  private def handleUnmatched[F[_]: Sync](serviceMetrics: ServiceMetrics): F[Option[Response[F]]] =
    Sync[F].delay(serviceMetrics.generalMetrics.activeRequests.decrement()).as(Option.empty[Response[F]])

  private def handleMatched[F[_]: Sync](resp: Response[F]): F[Option[Response[F]]] =
    resp.some.pure[F]

  private def responseTimer(responseTimers: ResponseTimeMetrics, status: Status): Histogram =
    status.code match {
      case hundreds if hundreds < 200 => responseTimers.resp1xx
      case twohundreds if twohundreds < 300 => responseTimers.resp2xx
      case threehundreds if threehundreds < 400 => responseTimers.resp3xx
      case fourhundreds if fourhundreds < 500 => responseTimers.resp4xx
      case _ => responseTimers.resp5xx
    }

  private def responseMetrics[F[_]: Sync](responseTimers: ResponseTimeMetrics, s: Status, elapsed: Long): F[Unit] =
    incrementCounts(responseTimer(responseTimers, s), elapsed)

  private def incrementCounts[F[_]: Sync](histogram: Histogram, elapsed: Long): F[Unit] =
    Sync[F].delay(histogram.record(elapsed))

  private def requestTimer[F[_]: Sync](rt: RequestTimeMetrics, method: Method) = method match {
    case Method.GET => rt.getRequest
    case Method.POST => rt.postRequest
    case Method.PUT => rt.putRequest
    case Method.HEAD => rt.headRequest
    case Method.MOVE => rt.moveRequest
    case Method.OPTIONS => rt.optionRequest
    case Method.TRACE => rt.traceRequest
    case Method.CONNECT => rt.connectRequest
    case Method.DELETE => rt.deleteRequest
    case _ => rt.otherRequest
  }

  private def requestMetrics[F[_]: Sync](rt: RequestTimeMetrics, active_requests: RangeSampler)(method: Method, elapsed: Long): F[Unit] = {
    val timer = requestTimer(rt, method)
    incrementCounts(timer, elapsed) *> incrementCounts(rt.totalRequest, elapsed) *> Sync[F].delay(active_requests.decrement())
  }

  //    Kleisli { request  =>
//      ActiveRequests.increment()
//      val incomingContext = decodeContext(request)
//      val serverSpan = Kamon.buildSpan("")
//        .asChildOf(incomingContext.get(Span.ContextKey))
//        .withMetricTag("span.kind", "server")
//        .withTag("http.method", request.method.name)
//        .withTag("http.url", request.uri.renderString)
//        .withTag("component", "http4s.server")
//        .start()
//
//      Kamon.withContext(Context.create(Span.ContextKey, serverSpan)) {
//        service(request)
////          .attempt
////          .flatMap(onFinish(serverSpan, Kamon.clock().instant())(_).fold(Task.fail, Task.now))
//      }
//    }
//  }





  //  private def onFinish(serverSpan: Span, start: Instant)(r: Attempt[MaybeResponse]): Attempt[MaybeResponse] = {
//
//    val endTimestamp = Kamon.clock().instant()
//    val elapsedTime = start.until(endTimestamp, ChronoUnit.NANOS)
//
//    r.map { response =>
//      val code = response.cata(_.status, Status.NotFound).code
//
//      serverSpan.tag("http.status_code", code)
//
//      def capture(body: EntityBody) = body.onFinalize[Task] {
//        Task.delay {
//          ActiveRequests.decrement()
//          if (code < 200) Responses1xx.record(elapsedTime)
//          else if (code < 300) Responses2xx.record(elapsedTime)
//          else if (code < 400) Responses3xx.record(elapsedTime)
//          else if (code < 500) {
//            if (code == StatusCodes.NotFound) serverSpan.setOperationName("not-found")
//            Responses4xx.record(elapsedTime)
//          } else {
//            serverSpan.addError("error")
//            Responses5xx.record(elapsedTime)
//          }
//
//          serverSpan.finish(endTimestamp)
//        }
//      }.onError { cause =>
//        AbnormalTermination.record(elapsedTime)
//        serverSpan.addError("abnormal-termination", cause).finish()
//        Stream.fail(cause)
//      }
//      response.cata(resp => resp.copy(body = capture(resp.body)), response)
//    }.leftMap { error =>
//      serverSpan.addError(error.getMessage, error).finish()
//      Responses5xx.record(elapsedTime)
//      error
//    }
//  }
}

