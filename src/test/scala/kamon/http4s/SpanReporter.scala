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

import kamon.Kamon
import kamon.module.Module.Registration
import kamon.testkit.{Reconfigure, TestSpanReporter}

trait SpanReporter extends Reconfigure {

  @volatile var registration: Registration = _
  val reporter = new TestSpanReporter.BufferingSpanReporter()

  def start(): Unit = {
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.registerModule("test-reporter", reporter)
  }

   def stop(): Unit = {
    registration.cancel()
  }
}