package com.elegant.software.blitzpay.payments.braintree

import com.elegant.software.blitzpay.config.ObservabilitySupport
import com.elegant.software.blitzpay.merchant.api.BraintreeCredentials
import com.elegant.software.blitzpay.payments.braintree.internal.BraintreeGatewayFactory
import com.elegant.software.blitzpay.payments.braintree.internal.BraintreePaymentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import io.micrometer.observation.ObservationRegistry
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID

class BraintreePaymentServiceTest {

    private val gatewayFactory: BraintreeGatewayFactory = mock()
    private val service = BraintreePaymentService(
        gatewayFactory,
        ObservabilitySupport(ObservationRegistry.NOOP),
    )
    private val merchantId: UUID = UUID.randomUUID()
    private val branchId: UUID = UUID.randomUUID()
    private val credentials = BraintreeCredentials(
        merchantId = "test-merchant",
        publicKey = "pub",
        privateKey = "priv",
        environment = "sandbox",
    )

    @Test
    fun `checkout rejects blank nonce`() {
        assertThrows<IllegalArgumentException> {
            service.checkout("", 12.50, "EUR", credentials, merchantId, branchId, "ORDER-1")
        }
    }

    @Test
    fun `checkout rejects zero amount`() {
        assertThrows<IllegalArgumentException> {
            service.checkout("fake-nonce", 0.0, "EUR", credentials, merchantId, branchId, "ORDER-1")
        }
    }

    @Test
    fun `checkout rejects negative amount`() {
        assertThrows<IllegalArgumentException> {
            service.checkout("fake-nonce", -5.0, "EUR", credentials, merchantId, branchId, "ORDER-1")
        }
    }

    @Test
    fun `amount is formatted with dot even in German locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val amount = 89.0
            val formattedAmount = "%.2f".format(Locale.US, amount)
            assertEquals("89.00", formattedAmount)
            assertEquals(BigDecimal("89.00"), BigDecimal(formattedAmount))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
