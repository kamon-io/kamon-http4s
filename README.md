# Kamon Http4s <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/> 
[![Build Status](https://travis-ci.org/kamon-io/kamon-http4s.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-http4s)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-http4s_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-http4s_2.12)


### Getting Started

The `kamon-http4s` module ships with bytecode instrumentation that brings automatic traces and metrics to your 
Http4s based applications.


The <b>kamon-http4s</b> module requires you to start your application using the Kamon Agent. Kamon will warn you
at startup if you failed to do so.

Kamon Http4s is currently available for Scala 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon  | status | jdk  | scala            
|:------:|:------:|:----:|------------------
|  1.0.0 | experimental | 1.8+ | 2.11, 2.12

To get started with SBT, simply add the following to your `build.sbt` or `pom.xml`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-http4s" % "1.0.0"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-http4s_2.12</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Documentation

### Server Metrics ###

The metrics that you will get are:

* __TODO__: 

### Traces ###



