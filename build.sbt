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

val kamonCore    = "io.kamon" %% "kamon-core"                   % "2.2.3"
val kamonTestkit = "io.kamon" %% "kamon-testkit"                % "2.2.3"
val kamonCommon  = "io.kamon" %% "kamon-instrumentation-common" % "2.2.3"

def http4sDeps(version: String) = Seq(
  "org.http4s" %% "http4s-client"       % version % Provided,
  "org.http4s" %% "http4s-server"       % version % Provided,
  "org.http4s" %% "http4s-blaze-client" % version % Test,
  "org.http4s" %% "http4s-blaze-server" % version % Test,
  "org.http4s" %% "http4s-dsl"          % version % Test
)

lazy val shared = Seq(
  scalaVersion := "2.13.6",
  crossScalaVersions := Seq("2.12.14", "2.13.6"),
  moduleName := name.value,
  publishTo := sonatypePublishToBundle.value,
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Ypartial-unification", "-language:higherKinds")
    case _             => "-language:higherKinds" :: Nil
  }),
  libraryDependencies ++=
    compileScope(kamonCore, kamonCommon) ++
      testScope(scalatest, kamonTestkit, logbackClassic)
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
    libraryDependencies ++= http4sDeps("0.23.1")
  )

lazy val `kamon-http4s-1_0` = project
  .in(file("modules/1.0"))
  .settings(
    shared,
    name := "kamon-http4s-1.0",
    libraryDependencies ++= http4sDeps("1.0.0-M24")
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
