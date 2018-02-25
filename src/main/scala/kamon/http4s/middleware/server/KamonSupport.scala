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

package kamon.http4s.middleware.server

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import kamon.Kamon
import kamon.context.Context
import kamon.http4s.Metrics.{GeneralMetrics, RequestTimeMetrics, ResponseTimeMetrics, ServiceMetrics}
import kamon.http4s.{Log, StatusCodes, decodeContext}
import kamon.metric.{Histogram, RangeSampler}
import kamon.trace.Span
import org.http4s.{HttpService, Method, Request, Response, Status}

object KamonSupport {

  def apply[F[_]:Sync](service: HttpService[F]):HttpService[F] = {
    import Log._

    val serviceMetrics = ServiceMetrics(GeneralMetrics(), RequestTimeMetrics(), ResponseTimeMetrics())
    Kleisli(kamonService[F](serviceMetrics, service)(_))
  }

  private def kamonService[F[_]](serviceMetrics: ServiceMetrics, service: HttpService[F])
                                             (req: Request[F])
                                             (implicit F: Sync[F], L: Log[F]): OptionT[F, Response[F]] = OptionT {
    for {
      now <- F.delay(Kamon.clock().instant())
      serverSpan <- createSpan(req)
      _ <- F.delay(serviceMetrics.generalMetrics.activeRequests.increment())
      scope <- F.delay(Kamon.storeContext(Context.create(Span.ContextKey, serverSpan)))
      e <- service(req).value.attempt
      _ <- F.delay(scope.close())
      resp <- kamonServiceHandler(req.method, now, serviceMetrics, serverSpan, e)
    } yield resp
  }

  private def kamonServiceHandler[F[_]](method: Method,
                                        start: Instant,
                                        serviceMetrics: ServiceMetrics,
                                        serverSpan: Span,
                                        e: Either[Throwable, Option[Response[F]]])
                                       (implicit F: Sync[F], L: Log[F]): F[Option[Response[F]]] = {
    for {
      elapsed <- EitherT.liftF[F, Throwable, Long](F.delay(start.until(Kamon.clock().instant(), ChronoUnit.NANOS)))
      respOpt <- EitherT(e.bitraverse[F, Throwable, Option[Response[F]]](
        manageServiceErrors(method, elapsed, serviceMetrics).as(_),
        _.map(manageResponse(method, start, elapsed, serviceMetrics, serverSpan)).pure[F]
      ))
    } yield respOpt
  }.fold(
    Sync[F].raiseError[Option[Response[F]]],_.fold(handleUnmatched(serviceMetrics))(handleMatched)
  ).flatten

  private def manageResponse[F[_]](m: Method,
                                   start: Instant,
                                   elapsedInit: Long,
                                   serviceMetrics: ServiceMetrics,
                                   spanServer:Span)
                                   (response: Response[F])
                                   (implicit F: Sync[F], L: Log[F]): Response[F] = {
    val newBody = response.body
      .onFinalize {
        for {
          endTimestamp <- F.delay(Kamon.clock().instant())
          elapsed <- F.delay(start.until(endTimestamp, ChronoUnit.NANOS))
          _ <- incrementCounts(serviceMetrics.generalMetrics.headersTimes, elapsedInit)
          _ <- requestMetrics(serviceMetrics.requestTimeMetrics, serviceMetrics.generalMetrics.activeRequests)(m, elapsed)
          _ <- responseMetrics(serviceMetrics.responseTimeMetrics, response.status, elapsed)
          _ <- finishSpan(spanServer, response.status, endTimestamp)
          _ <- L.debug(s"HTTP Response Time: $elapsed ns")
        } yield ()
      }
      .handleErrorWith(
        e =>
          Stream.eval(
            incrementCounts(serviceMetrics.generalMetrics.abnormalTerminations, elapsedInit)) *>
            Stream.raiseError[Byte](e))
    response.copy(body = newBody)
  }

  private def createSpan[F[_]](request: Request[F])
                              (implicit F: Sync[F]): F[Span] = {
    for {
      incomingContext <- decodeContext(request)
      operationName <- kamon.http4s.Http4s.generateOperationName(request)
      serverSpan <- F.delay(Kamon.buildSpan(operationName)
        .asChildOf(incomingContext.get(Span.ContextKey))
        .withMetricTag("span.kind", "server")
        .withMetricTag("component", "http4s.server")
        .withTag("http.method", request.method.name)
        .withTag("http.url", request.uri.renderString)
        .start())
    } yield serverSpan
  }

  private def finishSpan[F[_]](serverSpan:Span,
                               status: Status,
                               endTimestamp: Instant)
                              (implicit F: Sync[F]): F[Unit] =
    F.delay(serverSpan.tag("http.status_code", status.code)) *>
      handleStatusCode(serverSpan, status.code) *> F.delay(serverSpan.finish(endTimestamp))


  private def handleStatusCode[F[_]](span: Span, code:Int)(implicit F: Sync[F]):F[Unit] =
    F.delay {
      if (code < 500) {
        if (code == StatusCodes.NotFound) span.setOperationName("not-found")
      } else {
        span.addError("error")
      }
    }

  private def manageServiceErrors[F[_]](m: Method, elapsed: Long, serviceMetrics: ServiceMetrics)
                                       (implicit F: Sync[F]): F[Unit] =
    requestMetrics(serviceMetrics.requestTimeMetrics, serviceMetrics.generalMetrics.activeRequests)(m,elapsed) *>
      incrementCounts(serviceMetrics.generalMetrics.serviceErrors, elapsed)

  private def handleUnmatched[F[_]](serviceMetrics: ServiceMetrics)
                                   (implicit F: Sync[F]): F[Option[Response[F]]] =
    F.delay(serviceMetrics.generalMetrics.activeRequests.decrement()).as(Option.empty[Response[F]])

  private def handleMatched[F[_]: Sync](resp: Response[F]): F[Option[Response[F]]] =
    resp.some.pure[F]

  private def responseTime(responseTime: ResponseTimeMetrics, status: Status): Histogram =
    status.code match {
      case hundreds if hundreds < 200 => responseTime.forStatusCode("1xx")
      case twohundreds if twohundreds < 300 => responseTime.forStatusCode("2xx")
      case threehundreds if threehundreds < 400 => responseTime.forStatusCode("3xx")
      case fourhundreds if fourhundreds < 500 => responseTime.forStatusCode("4xx")
      case _ => responseTime.forStatusCode("5xx")
    }

  private def responseMetrics[F[_]](responseTimers: ResponseTimeMetrics, s: Status, elapsed: Long)
                                   (implicit F: Sync[F]): F[Unit] =
      incrementCounts(responseTime(responseTimers, s), elapsed)

  private def incrementCounts[F[_]](histogram: Histogram, elapsed: Long)
                                   (implicit F: Sync[F]): F[Unit] =
    F.delay(histogram.record(elapsed))

  private def requestTime(rt: RequestTimeMetrics, method: Method) = {
    rt.forMethod(method.name.toLowerCase())
  }

  private def requestMetrics[F[_]](rt: RequestTimeMetrics, activeRequests: RangeSampler)
                                        (method: Method, elapsed: Long)
                                        (implicit F: Sync[F]): F[Unit] = {
    val timer = requestTime(rt, method)
    incrementCounts(timer, elapsed) *> incrementCounts(rt.forMethod("total"), elapsed) *> F.delay(activeRequests.decrement())
  }
}
