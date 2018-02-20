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
import kamon.metric.MeasurementUnit._
import kamon.metric.{Histogram, HistogramMetric, RangeSampler}


object Metrics {

  /**
    * General Metrics for http4s Server:
    *
    * - active-requests: The the number active requests.
    * - abnormal-termination:The number of abnormal requests termination.
    * - service-errors:The number of service errors.
    * - headers-times:The number of abnormal requests termination.
    */

  val activeRequestsMetric = Kamon.rangeSampler("active-requests")
  val abnormalTerminationMetric = Kamon.histogram("abnormal-termination")
  val serviceErrorsMetric = Kamon.histogram("service-errors")
  val headersTimesMetric = Kamon.histogram("headers-times")

  case class GeneralMetrics(tags: Map[String, String],
                            activeRequests: RangeSampler,
                            abnormalTerminations: Histogram,
                            serviceErrors: Histogram,
                            headersTimes: Histogram)


  object GeneralMetrics {
    def apply(): GeneralMetrics = {
      val generalTags = Map("component" -> "http4s-server")
      new GeneralMetrics(
        generalTags,
        activeRequestsMetric.refine(generalTags),
        abnormalTerminationMetric.refine(generalTags),
        serviceErrorsMetric.refine(generalTags),
        headersTimesMetric.refine(generalTags))
    }
  }

  /**
    * Response Metrics for http4s Server:
    *
    * - http-responses: Response time by status code.
    */
  case class ResponseTimeMetrics(responseTimeMetric:HistogramMetric) {
    def forStatusCode(statusCode: String): Histogram = {
      val responseMetricsTags = Map("component" -> "http4s-server", "status-code" -> statusCode)
      responseTimeMetric.refine(responseMetricsTags)
    }
  }

  object ResponseTimeMetrics {
    def apply(): ResponseTimeMetrics =
      new ResponseTimeMetrics(Kamon.histogram("http-responses", time.nanoseconds))
  }


  /**
    * Request Metrics for http4s Server:
    *
    * - http-request: Request time by status code.
    */

  case class RequestTimeMetrics(requestTimeMetric:HistogramMetric) {
    def forMethod(method: String): Histogram = {
      val requestMetricsTags = Map("component" -> "http4s-server", "method" -> method)
      requestTimeMetric.refine(requestMetricsTags)
    }
  }

  object RequestTimeMetrics {
    def apply(): RequestTimeMetrics =
      new RequestTimeMetrics(Kamon.histogram("http-request", time.nanoseconds))
  }

  case class ServiceMetrics(generalMetrics: GeneralMetrics,
                            requestTimeMetrics: RequestTimeMetrics,
                            responseTimeMetrics: ResponseTimeMetrics)
}
