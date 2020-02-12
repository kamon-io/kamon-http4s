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

val kamonCore         = "io.kamon"    %% "kamon-core"                     % "2.0.4"
val kamonTestkit      = "io.kamon"    %% "kamon-testkit"                  % "2.0.4"
val kamonCommon       = "io.kamon"    %% "kamon-instrumentation-common"   % "2.0.1"

val server            = "org.http4s"  %%  "http4s-blaze-server"   % "0.21.0"
val client            = "org.http4s"  %%  "http4s-blaze-client"   % "0.21.0"
val dsl               = "org.http4s"  %%  "http4s-dsl"            % "0.21.0"


lazy val root = (project in file("."))
  .settings(Seq(
      name := "kamon-http4s",
      scalaVersion := "2.13.1",
      crossScalaVersions := Seq("2.12.10", "2.13.1")))
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(resolvers += Resolver.mavenLocal)
  .settings(scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Ypartial-unification", "-language:higherKinds")
    case _ => "-language:higherKinds" :: Nil
  }))
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kamonCommon) ++
      providedScope(server, client, dsl) ++
      testScope(scalatest, kamonTestkit, logbackClassic))
