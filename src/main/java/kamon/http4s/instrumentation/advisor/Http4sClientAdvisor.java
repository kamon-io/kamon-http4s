package kamon.http4s.instrumentation.advisor;

import kamon.agent.libs.net.bytebuddy.asm.Advice;
import kamon.agent.libs.net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import kamon.http4s.instrumentation.HttpServiceWrapper;

public class Http4sClientAdvisor {
    @Advice.OnMethodEnter
    public static void enter(@Advice.Argument(value = 0, readOnly = false, typing = Typing.DYNAMIC) Object httpService) {
        httpService = HttpServiceWrapper.wrap(httpService);
    }
}
