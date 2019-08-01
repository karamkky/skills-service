package skills.profile

import callStack.profiler.CProf
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.CodeSignature
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

import javax.servlet.http.HttpServletRequest

@Aspect
@Component
@Slf4j
@CompileStatic
class CallStackProfAspect {

    @Value('#{"${skills.prof.enabled:false}"}')
    boolean enabled

    @Value('#{"${skills.prof.minMillisToPrint:100}"}')
    int minMillisToPrint

    @Around("@within(EnableCallStackProf) || @annotation(EnableCallStackProf)")
    Object profile(ProceedingJoinPoint joinPoint) {
        if (!enabled) {
            return joinPoint.proceed()
        }

        Object retVal
        CProf.clear()
        String profileName = getProfileName(joinPoint)
        CProf.start(profileName)
        try {
            retVal = joinPoint.proceed();
        } finally {
            CProf.stop(profileName)
        }

        if (CProf.rootEvent.getRuntimeInMillis() > minMillisToPrint) {
            log.info("\nProfiling Endpoint: {}\n{}", getServletRequestPath(), CProf.prettyPrint())
        }
        return retVal
    }

    private String getServletRequestPath() {
        HttpServletRequest httpServletRequest
        try {
            ServletRequestAttributes currentRequestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
            httpServletRequest = currentRequestAttributes.getRequest()
        } catch (Exception e) {
            log.warn("Unable to access current HttpServletRequest. Error Recieved [$e]")
        }
        return httpServletRequest?.getServletPath() ?: "Could not be determined :("
    }

    private String getProfileName(ProceedingJoinPoint joinPoint) {
        StringBuilder builder = new StringBuilder()
        CodeSignature codeSignature = (CodeSignature)joinPoint.getSignature()
        builder.append(codeSignature.name)
        builder.append("(")

        String [] parameterNames = codeSignature.getParameterNames()
        for (int i = 0; i < codeSignature.parameterNames.length; i++) {
            if (i > 0) {
                builder.append(",")
            }
            String key = parameterNames[i]
            String value = joinPoint.getArgs()[i]
            builder.append(key)
            builder.append("=")
            builder.append(value)
        }

        builder.append(")")
        return  builder.toString()
    }

}
