package com.elegant.software.blitzpay.order

import com.elegant.software.blitzpay.order.api.CreateOrderRequest
import com.elegant.software.blitzpay.order.api.PaymentStatusChanged
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

object OrderFixtureLoader {
    private val objectMapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .addModule(JavaTimeModule())
        .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    fun createOrderRequest(): CreateOrderRequest = read("testdata/order/create-order/canonical-request.json")

    fun settledEvent(): PaymentStatusChanged = read("testdata/order/payment-status/settled-event.json")

    private inline fun <reified T> read(path: String): T {
        val resource = requireNotNull(this::class.java.classLoader.getResourceAsStream(path)) {
            "Missing fixture file: $path"
        }
        resource.use { return objectMapper.readValue(it) }
    }
}
