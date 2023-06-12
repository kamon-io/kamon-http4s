# Kamon http4s <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>
[![Discord](https://img.shields.io/discord/866301994074243132?label=Join%20the%20Comunity%20on%20Discord)](https://discord.gg/5JuYsDJ7au)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-http4s-0.23_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-http4s_2.12)


### Getting Started

The `kamon-http4s-<version>` module brings traces and metrics to your [http4s][4] based applications.

It is currently available for Scala 2.12, 2.13, and 3.x, depending on the http4s version. The current
version supports Kamon 2.6.1 and is published for http4s 0.22, 0.23, and 1.0.

| kamon | kamon-http4s  | status | jdk  | scala | http4s
|:-----:|:------:|:------:|:----:|--------------:|-------
| 2.6.x |  2.6.1 | stable | 8+ | 2.12, 2.13 | 0.22.x
| 2.6.x |  2.6.1 | stable | 8+ | 2.12, 2.13, 3.x | 0.23.x
| 2.6.x |  2.6.1 | stable | 8+ | 2.13, 3.x | 1.0.M31+

To get started with sbt, simply add the following to your `build.sbt` file, for instance for http4s 0.23:

```scala
libraryDependencies += "io.kamon" %% "kamon-http4s-0.23" % "2.6.1"
```

The releases and dependencies for the legacy module `kamon-http4s` (without http4s version) are shown below.

| kamon-http4s  | status | jdk  | scala | http4s            
|:------:|:------:|:----:|--------------:|-------
|  1.0.8-1.0.10 | stable | 8+ | 2.11, 2.12 | 0.18.x
|  1.0.13 | stable | 8+ | 2.11, 2.12 | 0.20.x
|  2.0.0 | stable | 8+ | 2.11, 2.12 | 0.20.x
|  2.0.1 | stable | 8+ | 2.12, 2.13 | 0.21.x


## Metrics and Tracing for http4s in 2 steps

### The Server

```scala
def serve[F[_]](implicit Effect: Effect[F], EC: ExecutionContext) : Stream[F, StreamApp.ExitCode] =
    for {
      _ <- Stream.eval(Sync[F].delay(println("Starting Google Service with Client")))
      client <- Http1Client.stream[F]()
      service = GoogleService.service[F](middleware.client.KamonSupport(client)) (1)
      exitCode <- BlazeBuilder[F]
        .bindHttp(Config.server.port, Config.server.interface)
        .mountService(middleware.server.KamonSupport(service)) (2)
        .serve
    } yield exitCode
```

* __(1)__: The Kamon [Middleware][5] for the `Client` side.
* __(2)__: The Kamon [Middleware][6] for the `Server` side.

### The Service

```scala
object GoogleService {
  def service[F[_]: Effect](c: Client[F]): HttpService[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._

    HttpService[F] {
      case GET -> Root / "call-google" =>
        Ok(c.expect[String]("https://www.google.com.ar"))
    }
  }
}
```

### Step 1: Add the Kamon Libraries
```scala
libraryDependencies ++= Seq(
  "io.kamon" %% "kamon-core" % "2.6.1",
  "io.kamon" %% "kamon-http4s-0.23" % "2.6.1",
  "io.kamon" %% "kamon-prometheus" % "2.6.1",
  "io.kamon" %% "kamon-zipkin" % "2.6.1",
  "io.kamon" %% "kamon-jaeger" % "2.6.1"
)
```

### Step 2: Start Reporting your Data

Since version 2.0, Kamon reporters are started automatically through their default configuration.
Now you can simply `sbt run` the application and after a few seconds you will get the Prometheus metrics
exposed on <http://localhost:9095/> and message traces sent to Zipkin! The default configuration publishes the Prometheus
endpoint on port 9095 and assumes that you have a Zipkin instance running locally on port 9411 but you can change these
values under the `kamon.prometheus` and `kamon.zipkin` configuration keys, respectively.


#### Metrics

All you need to do is [configure a scrape configuration in Prometheus][3]. The following snippet is a minimal
example that should work with the minimal server from the previous section.

```yaml
A minimal Prometheus configuration snippet
------------------------------------------------------------------------------
scrape_configs:
  - job_name: 'kamon-prometheus'
    static_configs:
      - targets: ['localhost:9095']
------------------------------------------------------------------------------
```

Once you configure this target in Prometheus you are ready to run some queries like this:

<img class="img-fluid" src="/doc/img/http4smetrics.png">

Those are the `Server Metrics` metrics that we are gathering by default:

* __active-requests__: The the number active requests.
* __http-responses__: Response time by status code.
* __abnormal-termination__: The number of abnormal requests termination.
* __service-errors__: The number of service errors.
* __headers-times__: The number of abnormal requests termination.
* __http-request__: Request time by status code.

Now you can go ahead, make your custom metrics and create your own dashboards!

#### Traces

Assuming that you have a Zipkin instance running locally with the default ports, you can go to <http://localhost:9411>
and start investigating traces for this application. Once you find a trace you are interested in you will see something
like this:

<img class="img-fluid" src="/doc/img/traces.png">

Clicking on a span will bring up a details view where you can see all tags for the selected span:

<img class="img-fluid" src="/doc/img/detail.png">


### Enjoy!

That's it, you are now collecting metrics and tracing information from a [http4s][4] application.

### Useful links:

[Example of how to correctly configure the execution context][7] by @cmcmteixeira

[1]: https://github.com/sbt/sbt-javaagent
[2]: https://github.com/kamon-io/kamon-agent
[3]: http://prometheus.io/docs/operating/configuration/#scrape-configurations-scrape_config
[4]: http://http4s.org
[5]: https://github.com/kamon-io/kamon-http4s/blob/master/src/main/scala/kamon/http4s/middleware/client/KamonSupport.scala
[6]: https://github.com/kamon-io/kamon-http4s/blob/master/src/main/scala/kamon/http4s/middleware/server/KamonSupport.scala
[7]: https://github.com/cmcmteixeira/http4s-traceid
