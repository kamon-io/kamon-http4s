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

import java.net.ConnectException

import cats.effect.{IO, Resource}
import kamon.Kamon
import kamon.context.Context
import kamon.http4s.middleware.client.KamonSupport
import kamon.tag.Lookups
import kamon.trace.Hooks.PreStart
import kamon.trace.Span
import org.http4s.{HttpRoutes, Response}
import org.http4s.client._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

class ClientInstrumentationSpec extends WordSpec
  with Matchers
  with Eventually
  with SpanSugar
  with OptionValues
  with SpanReporter
  with BeforeAndAfterAll {

  val service = HttpRoutes.of[IO] {
      case GET -> Root / "tracing" / "ok" =>  Ok("ok")
      case GET -> Root / "tracing" / "not-found"  => NotFound("not-found")
      case GET -> Root / "tracing" / "error"  => InternalServerError("This page will generate an error!")
  }

  val client: Client[IO] = KamonSupport[IO](Client.fromHttpApp[IO](service.orNotFound))

  "The Client instrumentation" should {
    "propagate the current context and generate a span inside an action and complete the ws request" in {
      val okSpan = Kamon.spanBuilder("ok-operation-span").start()

      Kamon.runWithContext(Context.of(Span.Key, okSpan)) {
        client.expect[String]("/tracing/ok").unsafeRunSync() shouldBe "ok"
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "/tracing/ok"
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"
        span.tags.get(Lookups.plainLong("http.status_code")) shouldBe 200

        okSpan.id == span.parentId
      }
    }

    "close and finish a span even if an exception is thrown by the client" in {
      val okSpan = Kamon.spanBuilder("client exception").start()
      val client: Client[IO] = KamonSupport[IO](
        Client(_ => Resource.liftF(IO.raiseError[Response[IO]](new ConnectException("Connection Refused."))))
      )

      Kamon.runWithContext(Context.of(Span.Key, okSpan)) {
        a[ConnectException] should be thrownBy {
          client.expect[String]("/tracing/ok").unsafeRunSync()
        }
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "/tracing/ok"
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"

        okSpan.id == span.parentId
      }
    }

    "propagate the current context and generate a span called not-found and complete the ws request" in {
      val notFoundSpan = Kamon.spanBuilder("not-found-operation-span").start()

      Kamon.runWithContext(Context.of(Span.Key, notFoundSpan)) {
        client.expect[String]("/tracing/not-found").attempt.unsafeRunSync().isLeft shouldBe true
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "not-found"
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"
        span.tags.get(Lookups.plainLong("http.status_code")) shouldBe 404

        notFoundSpan.id == span.parentId
      }
    }

    "propagate the current context and generate a span with error and complete the ws request" in {
      val errorSpan = Kamon.spanBuilder("error-operation-span").start()

      Kamon.runWithContext(Context.of(Span.Key, errorSpan)) {
        client.expect[String]("/tracing/error").attempt.unsafeRunSync().isLeft shouldBe true
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "/tracing/error"
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"
        span.metricTags.get(Lookups.plainBoolean("error")) shouldBe true
        span.tags.get(Lookups.plainLong("http.status_code")) shouldBe 500

        errorSpan.id == span.parentId
      }
    }

    "propagate the current context and pickup a PreStart hook and apply it to the new spans and complete the ws request" in {
      val okSpan = Kamon.spanBuilder("ok-operation-span").start()

      val customizedOperationName = "customized-operation-name"

      val context = Context.of(Span.Key, okSpan)
        .withEntry(PreStart.Key, PreStart.updateOperationName(customizedOperationName))

      Kamon.runWithContext(context) {
        client.expect[String]("/tracing/ok").unsafeRunSync shouldBe "ok"
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe customizedOperationName
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"
        span.tags.get(Lookups.plainLong("http.status_code")) shouldBe 200

        okSpan.id == span.parentId
      }
    }
  }

  def stringTag(span: Span.Finished)(tag: String): String = {
    span.metricTags.get(Lookups.plain(tag))
  }

  override protected def beforeAll(): Unit =
    start()

  override def afterAll: Unit = {
    stop()
  }
}
