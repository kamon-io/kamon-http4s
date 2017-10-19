/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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
import kamon.metric.MeasurementUnit._


object Metrics {


  /**
    * Metrics for http4s Server:
    *
    *    - abnormal-termination:The number of abnormal requests termination.
    *    - active-requests: The the number active requests.
    *    - http-responses: Response time by status code.
    */

  val  AbnormalTermination =  Kamon.histogram("abnormal-termination").refine(Map("component" -> "http4s-server"))
  val  ActiveRequests =  Kamon.minMaxCounter("active-requests").refine(Map("component" -> "http4s-server"))

  object ResponseMetrics {
    private val responseMetric = Kamon.histogram("http-responses", time.nanoseconds)

    val  Responses1xx =  responseMetric.refine(Map("component" -> "http4s-server", "status-code" -> "1xx"))
    val  Responses2xx =  responseMetric.refine(Map("component" -> "http4s-server", "status-code" -> "2xx"))
    val  Responses3xx =  responseMetric.refine(Map("component" -> "http4s-server", "status-code" -> "3xx"))
    val  Responses4xx =  responseMetric.refine(Map("component" -> "http4s-server", "status-code" -> "4xx"))
    val  Responses5xx =  responseMetric.refine(Map("component" -> "http4s-server", "status-code" -> "5xx"))
  }
}






