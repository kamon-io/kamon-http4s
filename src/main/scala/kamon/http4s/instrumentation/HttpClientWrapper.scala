package kamon.http4s.instrumentation

import cats.data.Kleisli
import cats.implicits._
import cats.effect.Effect
import kamon.Kamon
import kamon.http4s.Http4s
import kamon.trace.{Span, SpanCustomizer}
import org.http4s.Request
import org.http4s.client.{Client, DisposableResponse}

object HttpClientWrapper {
  def apply[F[_]: Effect](client: Client[F]): Client[F] = Client(open = HttpClientWrapper.wrap(client.open), shutdown = client.shutdown)

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
