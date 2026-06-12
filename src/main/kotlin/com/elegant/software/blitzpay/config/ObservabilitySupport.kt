package com.elegant.software.blitzpay.config

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component

@Component
class ObservabilitySupport(
    private val registry: ObservationRegistry,
) {
    fun <T> observe(
        name: String,
        contextualName: String,
        lowCardinality: Map<String, String> = emptyMap(),
        highCardinality: Map<String, String?> = emptyMap(),
        block: () -> T,
    ): T {
        val observation = Observation.createNotStarted(name, registry)
            .contextualName(contextualName)
        lowCardinality.forEach { (key, value) -> observation.lowCardinalityKeyValue(key, value) }
        highCardinality.forEach { (key, value) ->
            value?.takeIf(String::isNotBlank)?.let { observation.highCardinalityKeyValue(key, it) }
        }
        return observation.observe(block)
    }
}
