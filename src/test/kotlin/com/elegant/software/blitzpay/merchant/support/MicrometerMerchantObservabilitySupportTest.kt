package com.elegant.software.blitzpay.merchant.support

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MicrometerMerchantObservabilitySupportTest {

    private val meterRegistry = SimpleMeterRegistry()
    private val support = MicrometerMerchantObservabilitySupport(meterRegistry)

    @Test
    fun `records success counter tagged by action and status`() {
        support.recordSuccess("register", "ACTIVE")

        val counter = meterRegistry.get("merchant.actions")
            .tag("action", "register")
            .tag("status", "ACTIVE")
            .counter()

        assertEquals(1.0, counter.count())
    }

    @Test
    fun `records failure counter tagged by action and reason`() {
        support.recordFailure("register", "IllegalArgumentException")

        val counter = meterRegistry.get("merchant.failures")
            .tag("action", "register")
            .tag("reason", "IllegalArgumentException")
            .counter()

        assertEquals(1.0, counter.count())
    }
}
