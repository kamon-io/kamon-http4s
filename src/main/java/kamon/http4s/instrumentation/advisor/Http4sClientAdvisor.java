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

package kamon.http4s.instrumentation.advisor;

import cats.data.Kleisli;
import kamon.agent.libs.net.bytebuddy.asm.Advice;
import kamon.http4s.instrumentation.HttpClientWrapper;
import org.http4s.Request;
import org.http4s.client.DisposableResponse;

/**
 * Advisor for org.http4s.client.Client::<init>
 */
public class Http4sClientAdvisor {
    @Advice.OnMethodEnter
//    public static <F> void enter(@Advice.Argument(value = 0, readOnly = false) Kleisli<F, Request<F>, DisposableResponse<F>> httpService) {
    public static void enter(@Advice.Argument(value = 0, readOnly = false) Kleisli httpService) {
        httpService = HttpClientWrapper(httpService);
    }
}