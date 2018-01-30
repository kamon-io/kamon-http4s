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

import java.net.URL
import java.util.concurrent.Executors

import cats.effect.IO
import kamon.http4s.Metrics.ActiveRequests
import kamon.http4s.Metrics.ResponseMetrics._
import kamon.testkit.MetricInspection
import org.http4s.HttpService
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source


class HttpMetricsSpec extends WordSpec
  with Matchers
  with Eventually
  with SpanSugar
  with MetricInspection
  with OptionValues
  with SpanReporter
  with BeforeAndAfterAll {

  val server: Server[IO]=
    BlazeBuilder[IO]
      .bindAny()
      .withExecutionContext(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2)))
      .mountService(HttpService[IO] {
        case GET -> Root / "tracing" / "ok" =>  Ok("ok")
        case GET -> Root / "tracing" / "not-found"  => NotFound("not-found")
        case GET -> Root / "tracing" / "error"  => InternalServerError("This page will generate an error!")
      })
      .start
      .unsafeRunSync()

  private def get(path: String): String =
    Source
      .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
      .getLines
      .mkString


  val parallelRequestExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  "The HttpMetrics" should {
    "track the total of active requests" in {
      for(_ <- 1 to 10) yield  {
        Future { get("/tracing/ok") }(parallelRequestExecutor)
      }

      eventually(timeout(2 seconds)) {
        ActiveRequests.distribution().max shouldBe 10L
      }

      eventually(timeout(2 seconds)) {
        ActiveRequests.distribution().min shouldBe 0L
      }
      reporter.clear()
    }

    "track the response time with status code 2xx" in {
      for(_ <- 1 to 100) yield get("/tracing/ok")
      Responses2xx.distribution().max should be >= 0L
    }

    "track the response time with status code 4xx" in {
      for(_ <- 1 to 100) yield {
        intercept[Exception] {
          get("/tracing/not-found")
        }
      }
      Responses4xx.distribution().max should be >= 0L
    }

    "track the response time with status code 5xx" in {
      for(_ <- 1 to 100) yield {
        intercept[Exception] {
          get("/tracing/error")
        }
      }
      Responses5xx.distribution().max should be >= 0L
    }
  }

  override def afterAll: Unit =
    server.shutdownNow()
}
