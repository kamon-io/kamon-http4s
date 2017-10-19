package kamon.http4s

import java.net.URL
import java.util.concurrent.Executors

import fs2.Task
import kamon.testkit.{MetricInspection, Reconfigure}
import org.http4s.HttpService
import org.http4s.dsl.{Root, _}
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.ExecutionContext
import scala.io.Source


class ServerInstrumentationSpec extends WordSpec
  with Matchers
  with MetricInspection
  with Eventually
  with Reconfigure
  with OptionValues
  with BeforeAndAfterAll {

  val server: Server =
    BlazeBuilder
      .bindAny()
      .withExecutionContext(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4)))
      .mountService(HttpService {
        case GET -> Root / "thread" / "routing" =>
          val thread = Thread.currentThread.getName
          Ok(thread)

        case GET -> Root / "thread" / "effect" =>
          Task.delay(Thread.currentThread.getName).flatMap(Ok(_))

        case req @ POST -> Root / "echo" =>
          Ok(req.body)
      })
      .start
      .unsafeRun()


  private def get(path: String): String =
    Source
      .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
      .getLines
      .mkString


  override def afterAll = server.shutdownNow()

  "A server" should {
    "route requests on the service executor" in {
      get("/thread/routing") should startWith("pool-")
    }

    "execute the service task on the service executor" in {
      get("/thread/effect") should startWith("pool-")
    }

//    "be able to echo its input" in {
//      val input = """{ "Hello": "world" }"""
//      post("/echo", input) must startWith(input)
//    }
  }
}
