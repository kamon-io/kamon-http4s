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

import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import kamon.http4s.middleware.server.KamonSupport
import kamon.trace.Span
import kamon.trace.Span.TagValue
import org.http4s.{Headers, HttpRoutes}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.ExecutionContext
import org.http4s.implicits._
import cats.implicits._
import org.http4s.util.CaseInsensitiveString

class ServerInstrumentationSpec extends WordSpec
  with Matchers
  with Eventually
  with SpanSugar
  with OptionValues
  with SpanReporter
  with BeforeAndAfterAll {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  val srv =
    BlazeServerBuilder[IO]
      .bindAny()
      .withExecutionContext(ExecutionContext.global)
      .withHttpApp(KamonSupport(HttpRoutes.of[IO] {
          case GET -> Root / "tracing" / "ok" =>  Ok("ok")
          case GET -> Root / "tracing" / "not-found"  => NotFound("not-found")
          case GET -> Root / "tracing" / "error"  => InternalServerError("error!")
        }
      ).orNotFound)
    .resource

  val client =
    BlazeClientBuilder[IO](ExecutionContext.global).resource

  def withServerAndClient[A](f: (Server[IO], Client[IO]) => IO[A]): A =
    (srv, client).tupled.use(f.tupled).unsafeRunSync()

  private def getResponse[F[_]: ConcurrentEffect](path: String)(server: Server[F], client: Client[F]): F[(String, Headers)] = {
    client.get(s"http://127.0.0.1:${server.address.getPort}$path"){ r =>
      r.bodyAsText.compile.toList.map(_.mkString).map(_ -> r.headers)
    }
  }

  "The Server instrumentation" should {
    "propagate the current context and respond to the ok action" in withServerAndClient { (server, client) =>
      val request = getResponse("/tracing/ok")(server, client).map { case (body, headers) =>
        headers.exists(_.name == CaseInsensitiveString("X-B3-TraceId")) shouldBe true
        headers.exists(_.name == CaseInsensitiveString("X-B3-Sampled")) shouldBe true
        headers.exists(_.name == CaseInsensitiveString("X-B3-SpanId")) shouldBe true
        body should startWith("ok")
      }

      val test = IO {
        eventually(timeout(5.seconds)) {
          val span = reporter.nextSpan().value
          val spanTags = stringTag(span) _

          span.operationName shouldBe "tracing.ok.get"
          spanTags("span.kind") shouldBe "server"
          spanTags("component") shouldBe "http4s.server"
          spanTags("http.method") shouldBe "GET"
          span.tags("http.status_code") shouldBe TagValue.Number(200)
        }
      }

      request *> test
    }

    "propagate the current context and respond to the not-found action" in withServerAndClient { (server, client) =>
      val request = getResponse("/tracing/not-found")(server, client).map { case (body, headers) =>
        headers.exists(_.name == CaseInsensitiveString("X-B3-TraceId")) shouldBe true
        headers.exists(_.name == CaseInsensitiveString("X-B3-Sampled")) shouldBe true
        headers.exists(_.name == CaseInsensitiveString("X-B3-SpanId")) shouldBe true
        body should startWith("not-found")
      }

      val test = IO {
        eventually(timeout(5.seconds)) {
          val span = reporter.nextSpan().value
          val spanTags = stringTag(span) _

          span.operationName shouldBe "not-found"
          spanTags("span.kind") shouldBe "server"
          spanTags("component") shouldBe "http4s.server"
          spanTags("http.method") shouldBe "GET"
          span.tags("http.status_code") shouldBe TagValue.Number(404)
        }
      }

      request *> test
    }

    "propagate the current context and respond to the error action" in withServerAndClient { (server, client) =>
      val request = getResponse("/tracing/error")(server, client).map { case (body, headers) =>
        headers.exists(_.name == CaseInsensitiveString("X-B3-TraceId")) shouldBe true
        headers.exists(_.name == CaseInsensitiveString("X-B3-Sampled")) shouldBe true
        headers.exists(_.name == CaseInsensitiveString("X-B3-SpanId")) shouldBe true
        body should startWith("error!")
      }

      val test = IO {
        eventually(timeout(5.seconds)) {
          val span = reporter.nextSpan().value
          val spanTags = stringTag(span) _

          span.operationName shouldBe "tracing.error.get"
          spanTags("span.kind") shouldBe "server"
          spanTags("component") shouldBe "http4s.server"
          spanTags("http.method") shouldBe "GET"
          span.tags("error") shouldBe TagValue.True
          span.tags("http.status_code") shouldBe TagValue.Number(500)
        }
      }

      request *> test
    }
  }

  def stringTag(span: Span.FinishedSpan)(tag: String): String = {
    span.tags(tag).asInstanceOf[TagValue.String].string
  }

  override protected def beforeAll(): Unit = {
    start()
  }

  override def afterAll: Unit = {
    stop()
  }
}
