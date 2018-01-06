package kamon.http4s.instrumentation.advisor;

import kamon.agent.libs.net.bytebuddy.asm.Advice;
import kamon.agent.libs.net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import kamon.http4s.instrumentation.HttpServerServiceWrapper;

/**
 * Advisor for org.http4s.server.Router$::apply
 */
public class Http4sServerAdvisor {
    @Advice.OnMethodExit
    public static void exit(@Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object httpService) {
        httpService = HttpServerServiceWrapper.wrap(httpService);
    }
}
