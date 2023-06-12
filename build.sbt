/* =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
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
import xerial.sbt.Sonatype._

val kamonVersion = "2.6.1"
val kamonCore = "io.kamon" %% "kamon-core" % kamonVersion
val kamonTestkit = "io.kamon" %% "kamon-testkit" % kamonVersion
val kamonCommon = "io.kamon" %% "kamon-instrumentation-common" % kamonVersion

val scalatestLocal = "org.scalatest" %% "scalatest" % "3.2.15"

def http4sDeps(http4sVersion: String, blazeVersion: String) = Seq(
  "org.http4s" %% "http4s-client" % http4sVersion % Provided,
  "org.http4s" %% "http4s-server" % http4sVersion % Provided,
  "org.http4s" %% "http4s-blaze-client" % blazeVersion % Test,
  "org.http4s" %% "http4s-blaze-server" % blazeVersion % Test,
  "org.http4s" %% "http4s-dsl" % http4sVersion % Test
)

lazy val shared = Seq(
  organization := "io.kamon",
  scalaVersion := "2.13.8",
  crossScalaVersions := Seq("2.12.14", "2.13.8"),
  moduleName := name.value,
  publishTo := sonatypePublishToBundle.value,
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Ypartial-unification", "-language:higherKinds")
    case Some((3, _))  => Seq("-source:3.0-migration", "-Xtarget:8")
    case _             => "-language:higherKinds" :: Nil
  }),
  libraryDependencies ++= Seq(kamonCore, kamonCommon) ++ Seq(
    scalatestLocal,
    kamonTestkit
  ).map(_ % Test),
  publishMavenStyle := true,
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://kamon.io")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/kamon-io/kamon-http4s"),
      "scm:git@github.com:kamon-io/kamon-http4s.git"
    )
  ),
  developers := List(
    Developer(id="ivantopo", name="Ivan Topolnjak", url=url("https://twitter.com/ivantopo"), email=""),
    Developer(id="dpsoft", name="Diego Parra", url=url("https://twitter.com/dpsoft"), email=""),
    Developer(id="vaslabs", name="Vasilis Nicolaou", url=url("https://github.com/vaslabs"), email=""),
    Developer(id="jchapuis", name="Jonas Chapuis", url=url("https://github.com/jchapuis"), email="")
  )
)

lazy val `kamon-http4s-0_22` = project
  .in(file("modules/0.22"))
  .settings(
    shared,
    name := "kamon-http4s-0.22",
    libraryDependencies ++= http4sDeps("0.22.15", "0.22.15")
  )

lazy val `kamon-http4s-0_23` = project
  .in(file("modules/0.23"))
  .settings(
    shared,
    name := "kamon-http4s-0.23",
    crossScalaVersions += "3.3.0",
    libraryDependencies ++= http4sDeps("0.23.19", "0.23.14")
  )

lazy val `kamon-http4s-1_0` = project
  .in(file("modules/1.0"))
  .settings(
    shared,
    name := "kamon-http4s-1.0",
    crossScalaVersions := Seq("2.13.8", "3.3.0"),
    libraryDependencies ++= http4sDeps("1.0.0-M38", "1.0.0-M38")
  )

lazy val root = project
  .in(file("."))
  .settings(
    shared,
    name := "kamon-http4s",
    publish / skip := true,
    Test / parallelExecution := false,
    Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)
  )
  .aggregate(`kamon-http4s-0_22`, `kamon-http4s-0_23`, `kamon-http4s-1_0`)
