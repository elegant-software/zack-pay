package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MicrometerMerchantObservabilitySupportTest {

    private val meterRegistry = SimpleMeterRegistry()
    private val support = MicrometerMerchantObservabilitySupport(meterRegistry)

    @Test
    fun `records success counter tagged by action and status`() {
        support.recordSuccess("submit", MerchantOnboardingStatus.SUBMITTED)

        val counter = meterRegistry.get("merchant.onboarding.actions")
            .tag("action", "submit")
            .tag("status", "SUBMITTED")
            .counter()

        assertEquals(1.0, counter.count())
    }

    @Test
    fun `records failure counter tagged by action and reason`() {
        support.recordFailure("submit", "IllegalArgumentException")

        val counter = meterRegistry.get("merchant.onboarding.failures")
            .tag("action", "submit")
            .tag("reason", "IllegalArgumentException")
            .counter()

        assertEquals(1.0, counter.count())
    }
}
