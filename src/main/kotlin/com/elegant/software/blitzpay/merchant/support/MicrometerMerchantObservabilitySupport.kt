package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MicrometerMerchantObservabilitySupport(
    private val meterRegistry: MeterRegistry
) : MerchantObservabilitySupport {
    private val logger = LoggerFactory.getLogger(MicrometerMerchantObservabilitySupport::class.java)

    override fun recordSuccess(action: String, status: MerchantOnboardingStatus) {
        meterRegistry.counter(
            "merchant.onboarding.actions",
            "action", action,
            "status", status.name
        ).increment()

        logger.info("merchant_observability action={} status={} result=success", action, status.name)
    }

    override fun recordFailure(action: String, reason: String) {
        meterRegistry.counter(
            "merchant.onboarding.failures",
            "action", action,
            "reason", reason
        ).increment()

        logger.warn("merchant_observability action={} result=failure reason={}", action, reason)
    }
}
