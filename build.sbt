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
val kamonVersion = "2.5.7"
val kamonCore    = "io.kamon" %% "kamon-core"                   % kamonVersion
val kamonTestkit = "io.kamon" %% "kamon-testkit"                % kamonVersion
val kamonCommon  = "io.kamon" %% "kamon-instrumentation-common" % kamonVersion

val scalatestLocal = "org.scalatest"    %% "scalatest"       % "3.2.12"

def http4sDeps(version: String) = Seq(
  "org.http4s" %% "http4s-client"       % version % Provided,
  "org.http4s" %% "http4s-server"       % version % Provided,
  "org.http4s" %% "http4s-blaze-client" % version % Test,
  "org.http4s" %% "http4s-blaze-server" % version % Test,
  "org.http4s" %% "http4s-dsl"          % version % Test
)

lazy val shared = Seq(
  scalaVersion := "2.13.8",
  crossScalaVersions := Seq("2.12.14", "2.13.8"),
  moduleName := name.value,
  publishTo := sonatypePublishToBundle.value,
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Ypartial-unification", "-language:higherKinds")
    case Some((3, _)) => Seq("-source:3.0-migration", "-Xtarget:8")
    case _             => "-language:higherKinds" :: Nil
  }),
  libraryDependencies ++=
    compileScope(kamonCore, kamonCommon) ++
      testScope(scalatestLocal, kamonTestkit, logbackClassic)
)

lazy val `kamon-http4s-0_22` = project
  .in(file("modules/0.22"))
  .settings(
    shared,
    name := "kamon-http4s-0.22",
    libraryDependencies ++= http4sDeps("0.22.2")
  )

lazy val `kamon-http4s-0_23` = project
  .in(file("modules/0.23"))
  .settings(
    shared,
    name := "kamon-http4s-0.23",
    crossScalaVersions += "3.1.3",
    libraryDependencies ++= http4sDeps("0.23.12")
  )

lazy val `kamon-http4s-1_0` = project
  .in(file("modules/1.0"))
  .settings(
    shared,
    name := "kamon-http4s-1.0",
    crossScalaVersions += "3.1.3",
    libraryDependencies ++= http4sDeps("1.0.0-M35")
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
