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

import cats.effect.IO
import kamon.Kamon
import kamon.context.Context
import kamon.context.Context.create
import kamon.http4s.middleware.client.KamonSupport
import kamon.trace.Span.TagValue
import kamon.trace.{Span, SpanCustomizer}
import org.http4s.HttpService
import org.http4s.client.Client
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
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

  val service = HttpService[IO] {
      case GET -> Root / "tracing" / "ok" =>  Ok("ok")
      case GET -> Root / "tracing" / "not-found"  => NotFound("not-found")
      case GET -> Root / "tracing" / "error"  => InternalServerError("This page will generate an error!")
      case GET -> Root / "tracing" / "throw-exception"  =>
        new RuntimeException
        Ok()
  }

  val client: Client[IO] = KamonSupport[IO](Client.fromHttpService[IO](service))

  "The Client instrumentation" should {
    "propagate the current context and generate a span inside an action and complete the ws request" in {
      val okSpan = Kamon.buildSpan("ok-operation-span").start()

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        client.expect[String]("/tracing/ok").unsafeRunSync() shouldBe "ok"
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "None/tracing/ok"
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"
        span.tags("http.status_code") shouldBe TagValue.Number(200)

        okSpan.context.spanID == span.context.parentID
      }
    }

    "propagate the current context and generate a span called not-found and complete the ws request" in {
      val notFoundSpan = Kamon.buildSpan("not-found-operation-span").start()

      Kamon.withContext(create(Span.ContextKey, notFoundSpan)) {
        client.expect[String]("/tracing/not-found").attempt.unsafeRunSync().isLeft shouldBe true
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "not-found"
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"
        span.tags("http.status_code") shouldBe TagValue.Number(404)

        notFoundSpan.context.spanID == span.context.parentID
      }
    }

    "propagate the current context and generate a span with error and complete the ws request" in {
      val errorSpan = Kamon.buildSpan("error-operation-span").start()

      Kamon.withContext(create(Span.ContextKey, errorSpan)) {
        client.expect[String]("/tracing/error").attempt.unsafeRunSync().isLeft shouldBe true
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "None/tracing/error"
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"
        span.tags("error") shouldBe TagValue.True
        span.tags("http.status_code") shouldBe TagValue.Number(500)

        errorSpan.context.spanID == span.context.parentID
      }
    }

    "propagate the current context and pickup a SpanCustomizer and apply it to the new spans and complete the ws request" in {
      val okSpan = Kamon.buildSpan("ok-operation-span").start()

      val customizedOperationName = "customized-operation-name"

      val context = Context.create(Span.ContextKey, okSpan)
        .withKey(SpanCustomizer.ContextKey, SpanCustomizer.forOperationName(customizedOperationName))

      Kamon.withContext(context) {
        client.expect[String]("/tracing/ok").unsafeRunSync shouldBe "ok"
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe customizedOperationName
        spanTags("span.kind") shouldBe "client"
        spanTags("component") shouldBe "http4s.client"
        spanTags("http.method") shouldBe "GET"
        span.tags("http.status_code") shouldBe TagValue.Number(200)

        okSpan.context.spanID == span.context.parentID
      }
    }
  }

  def stringTag(span: Span.FinishedSpan)(tag: String): String = {
    span.tags(tag).asInstanceOf[TagValue.String].string
  }

  override protected def beforeAll(): Unit =
    start()

  override def afterAll: Unit = {
    stop()
    client.shutdownNow()
  }
}
