package com.elegant.software.blitzpay.config

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class RepositoryObservationAspect(
    private val registry: ObservationRegistry,
) {
    @Around("execution(* org.springframework.data.repository.Repository+.*(..))")
    fun observeRepositoryOperation(joinPoint: ProceedingJoinPoint): Any? {
        val repository = joinPoint.signature.declaringType.simpleName
        val operation = joinPoint.signature.name
        return Observation.createNotStarted("blitzpay.repository", registry)
            .contextualName("$repository.$operation")
            .lowCardinalityKeyValue("repository", repository)
            .lowCardinalityKeyValue("operation", operation)
            .observe<Any?> { joinPoint.proceed() }
    }
}
