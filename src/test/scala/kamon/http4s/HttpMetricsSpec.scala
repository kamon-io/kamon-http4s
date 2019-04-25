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

import cats.effect._
import kamon.http4s.Metrics.{GeneralMetrics, ResponseTimeMetrics}
import kamon.http4s.middleware.server.KamonSupport
import kamon.testkit.MetricInspection
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{Matchers, OptionValues, WordSpec}
import cats.implicits._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.Client

import scala.concurrent.ExecutionContext
import org.http4s.implicits._

class HttpMetricsSpec extends WordSpec
  with Matchers
  with Eventually
  with SpanSugar
  with MetricInspection
  with OptionValues
 {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  val srv =
    BlazeServerBuilder[IO]
      .bindAny()
      .withHttpApp(KamonSupport(HttpRoutes.of[IO] {
        case GET -> Root / "tracing" / "ok" =>  Ok("ok")
        case GET -> Root / "tracing" / "not-found"  => NotFound("not-found")
        case GET -> Root / "tracing" / "error"  => InternalServerError("This page will generate an error!")
      }).orNotFound)
      .resource

  val client =
    BlazeClientBuilder[IO](ExecutionContext.global).withMaxTotalConnections(10).resource

  def withServerAndClient[A](f: (Server[IO], Client[IO]) => IO[A]): A =
    (srv, client).tupled.use(f.tupled).unsafeRunSync()

  private def get[F[_]: ConcurrentEffect](path: String)(server: Server[F], client: Client[F]): F[String] = {
    client.expect[String](s"http://127.0.0.1:${server.address.getPort}$path")
  }

  "The HttpMetrics" should {
    "track the total of active requests" in withServerAndClient { (server, client) =>
      val requests = List
        .fill(100) {
          get("/tracing/ok")(server, client)
        }.parSequence_

      val test = IO {
        GeneralMetrics().activeRequests.distribution().max should be > 1L
        GeneralMetrics().activeRequests.distribution().min shouldBe 0L
      }
      requests *> test
    }

    "track the response time with status code 2xx" in withServerAndClient { (server, client) =>
      val requests: IO[Unit] = List.fill(100)(get("/tracing/ok")(server, client)).sequence_

      val test = IO(ResponseTimeMetrics().forStatusCode("2xx").distribution().max should be >= 0L)

      requests *> test
    }

    "track the response time with status code 4xx" in withServerAndClient { (server, client) =>
      val requests: IO[Unit] = List.fill(100)(get("/tracing/not-found")(server, client).attempt).sequence_

      val test = IO(ResponseTimeMetrics().forStatusCode("4xx").distribution().max should be >= 0L)

      requests *> test
    }

    "track the response time with status code 5xx" in withServerAndClient { (server, client) =>
      val requests: IO[Unit] = List.fill(100)(get("/tracing/error")(server, client).attempt).sequence_

      val test = IO(ResponseTimeMetrics().forStatusCode("5xx").distribution().max should be >= 0L)

      requests *> test
    }
  }
}
