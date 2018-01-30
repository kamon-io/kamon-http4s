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

import com.typesafe.config.Config
import kamon.Kamon
import kamon.util.DynamicAccess
import org.http4s.Request

object Http4s {
  @volatile private var nameGenerator: NameGenerator = new DefaultNameGenerator()

  loadConfiguration(Kamon.config())

  def generateOperationName[F[_]](request: Request[F]): String =
    nameGenerator.generateOperationName(request)

  def generateHttpClientOperationName[F[_]](request: Request[F]): String =
    nameGenerator.generateHttpClientOperationName(request)

  Kamon.onReconfigure((newConfig: Config) => Http4s.loadConfiguration(newConfig))

  private def loadConfiguration(config: Config): Unit = {
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val nameGeneratorFQCN = config.getString("kamon.http4s.name-generator")
    nameGenerator = dynamic.createInstanceFor[NameGenerator](nameGeneratorFQCN, Nil).get
  }
}

trait NameGenerator {
  def generateOperationName[F[_]](request: Request[F]): String
  def generateHttpClientOperationName[F[_]](request: Request[F]): String
}

class DefaultNameGenerator extends NameGenerator {

  import java.util.Locale

  import scala.collection.concurrent.TrieMap

  private val localCache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  override def generateHttpClientOperationName[F[_]](request: Request[F]): String = {
    s"${request.uri.authority}${request.uri.path}"
  }

  override def generateOperationName[F[_]](request: Request[F]): String = {
    localCache.getOrElseUpdate(s"${request.method.name}${request.uri.path}", {
      // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
      val uri = request.uri
      val p = normalizePattern.replaceAllIn(uri.path, "$1").replace('/', '.').dropWhile(_ == '.')
      val normalisedPath = {
        if (p.lastOption.exists(_ != '.')) s"$p."
        else p
      }
      s"$normalisedPath${request.method.name.toLowerCase(Locale.ENGLISH)}"
    })
  }
}
