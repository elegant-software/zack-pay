package com.elegant.software.blitzpay.config

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

/**
 * Adds provider spans around SDK-backed integrations that cannot use Spring's
 * automatically instrumented WebClient or RestClient builders.
 */
@Aspect
@Component
class SdkClientObservationAspect(
    private val registry: ObservationRegistry,
) {
    @Around(
        "execution(* com.elegant.software.blitzpay.payments.stripe.web.StripeConnectService.*(..)) || " +
            "execution(* com.elegant.software.blitzpay.payments.stripe.inbound.StripeSettlementClientImpl.*(..)) || " +
            "execution(* com.elegant.software.blitzpay.payments.stripe.web.StripeTerminalController.*(..))"
    )
    fun observeStripe(joinPoint: ProceedingJoinPoint): Any? = observe("stripe", joinPoint)

    @Around("execution(* com.elegant.software.blitzpay.merchant.iam.service.UserManagementService.*(..))")
    fun observeKeycloak(joinPoint: ProceedingJoinPoint): Any? = observe("keycloak", joinPoint)

    private fun observe(provider: String, joinPoint: ProceedingJoinPoint): Any? {
        val operation = joinPoint.signature.name
            .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
            .lowercase()
        return Observation.createNotStarted("blitzpay.external", registry)
            .contextualName("$provider $operation")
            .lowCardinalityKeyValue("provider", provider)
            .lowCardinalityKeyValue("operation", operation)
            .observe<Any?> { joinPoint.proceed() }
    }
}
