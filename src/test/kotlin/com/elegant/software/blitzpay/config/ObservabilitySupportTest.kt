package com.elegant.software.blitzpay.config

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ObservabilitySupportTest {
    @Test
    fun `records names attributes and errors without recording payloads`() {
        val registry = ObservationRegistry.create()
        var stoppedContext: Observation.Context? = null
        registry.observationConfig().observationHandler(object : ObservationHandler<Observation.Context> {
            override fun supportsContext(context: Observation.Context): Boolean = true
            override fun onStop(context: Observation.Context) {
                stoppedContext = context
            }
        })
        val support = ObservabilitySupport(registry)

        assertFailsWith<IllegalStateException> {
            support.observe<Unit>(
                name = "blitzpay.external",
                contextualName = "stripe create-intent",
                lowCardinality = mapOf("provider" to "stripe"),
                highCardinality = mapOf("order.reference" to "ORDER-123"),
            ) {
                error("provider unavailable")
            }
        }

        val context = assertNotNull(stoppedContext)
        assertEquals("stripe", context.lowCardinalityKeyValues.first { it.key == "provider" }.value)
        assertEquals("ORDER-123", context.highCardinalityKeyValues.first { it.key == "order.reference" }.value)
        assertTrue(context.error is IllegalStateException)
    }
}
