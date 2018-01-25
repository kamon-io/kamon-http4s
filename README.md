# Kamon http4s <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/> 
[![Build Status](https://travis-ci.org/kamon-io/kamon-http4s.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-http4s)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-http4s_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-http4s_2.12)


### Getting Started

The `kamon-http4s` module ships with bytecode instrumentation that brings automatic traces and metrics to your 
[http4s][4] based applications

The <b>kamon-http4s</b> module requires you to start your application using the [Kamon Agent][2].

Kamon <b>kamon-http4s</b> is currently available for Scala 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-http4s  | status | jdk  | scala | http4s            
|:------:|:------:|:----:|--------------:|-------
|  1.0.0 | experimental | 1.8+ | 2.12 | 0.17.x

| kamon-http4s  | status | jdk  | scala | http4s            
|:------:|:------:|:----:|--------------:|-------
|  1.0.0 | soon | 1.8+ | 2.12 | 0.18.x

To get started with SBT, simply add the following to your `build.sbt`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-http4s" % "1.0.0"
```

#### Metrics and Tracing for http4s in 3 steps

### The Service

```scala
object TheService {
  val httpClient = PooledHttp1Client()

  def service(): HttpService =
    HttpService {
      case GET -> Root / "ok" => Ok("ok")
      case GET -> Root / "call-google" => 
        val response = httpClient.expect[String]("https://www.google.com").unsafeRun
        Ok(response)
  }
}
```

### Step 1: Add the Kamon Libraries
```scala
libraryDependencies ++= Seq(
  "io.kamon" %% "kamon-core" % "1.0.0",
  "io.kamon" %% "kamon-system-metrics" % "1.0.0",
  "io.kamon" %% "kamon-prometheus" % "1.0.0",
  "io.kamon" %% "kamon-http4s" % "1.0.0",
  "io.kamon" %% "kamon-zipkin" % "1.0.0",
  "io.kamon" %% "kamon-jaeger" % "1.0.0"
)
```

### Step 2: Setting up the [Kamon Agent][2]

Here we will be running from SBT and just adding the [`sbt-javaagent`][1] plugin to the build is enough to get it
working.

```scala
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")

enablePlugins(JavaAgent)

javaAgents += "io.kamon" % "kamon-agent" % "0.0.8-experimental" % "runtime"
```

### Step 3: Start Reporting your Data

The last step in the process: start reporting your data! You can register as many reporters as you want by using the
`Kamon.addReporter(...)` function:

```scala
Kamon.addReporter(new PrometheusReporter())
Kamon.addReporter(new ZipkinReporter())
Kamon.addReporter(new Jaeger())
```

Now you can simply `sbt run` the application and after a few seconds you will get the Prometheus metrics
exposed on <http://localhost:9095/> and message traces sent to Zipkin! The default configuration publishes the Prometheus
endpoint on port 9095 and assumes that you have a Zipkin instance running locally on port 9411 but you can change these
values under the `kamon.prometheus` and `kamon.zipkin` configuration keys, respectively.


#### Metrics

All you need to do is [configure a scrape configuration in Prometheus][3]. The following snippet is a minimal
example that shold work with the minimal server from the previous section.

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


[kamon-agent-0.0.8-experimental.jar]:https://mvnrepository.com/artifact/io.kamon/kamon-agent/0.0.8-experimental
[1]: https://github.com/sbt/sbt-javaagent
[2]: https://github.com/kamon-io/kamon-agent
[3]: http://prometheus.io/docs/operating/configuration/#scrape-configurations-scrape_config
[4]: http://http4s.org
