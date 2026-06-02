package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.internal.StripePaymentService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class StripePaymentServiceTest {

    private val service = StripePaymentService(StripeProperties(secretKey = "sk_test_dummy", publishableKey = "pk_test_dummy"))
    private val connectedAccountId = "acct_test_dummy"
    private val merchantId: UUID = UUID.randomUUID()
    private val paymentRequestId: UUID = UUID.randomUUID()

    @Test
    fun `rejects zero amount`() {
        assertThrows<IllegalArgumentException> {
            service.createIntent(0.0, "eur", connectedAccountId, merchantId, null, "ORDER-1", paymentRequestId, null)
        }
    }

    @Test
    fun `rejects negative amount`() {
        assertThrows<IllegalArgumentException> {
            service.createIntent(-5.0, "eur", connectedAccountId, merchantId, null, "ORDER-1", paymentRequestId, null)
        }
    }

    @Test
    fun `rejects NaN amount`() {
        assertThrows<IllegalArgumentException> {
            service.createIntent(Double.NaN, "eur", connectedAccountId, merchantId, null, "ORDER-1", paymentRequestId, null)
        }
    }

    @Test
    fun `rejects infinite amount`() {
        assertThrows<IllegalArgumentException> {
            service.createIntent(Double.POSITIVE_INFINITY, "eur", connectedAccountId, merchantId, null, "ORDER-1", paymentRequestId, null)
        }
    }
}
