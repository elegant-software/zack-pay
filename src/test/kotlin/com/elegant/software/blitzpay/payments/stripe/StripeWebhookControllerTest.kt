package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusUpdateGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.inbound.StripePaymentSettled
import com.elegant.software.blitzpay.payments.stripe.inbound.StripeWebhookController
import com.stripe.Stripe
import com.stripe.net.Webhook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Stripe delivers webhook events at the endpoint's pinned API version, which can lag behind the
 * release pinned by stripe-java. These tests send events at an older release and assert they are
 * still processed.
 */
class StripeWebhookControllerTest {

    private val webhookSecret = "whsec_platform"
    private val connectWebhookSecret = "whsec_connect"
    private val olderApiVersion = "2026-05-27.dahlia"

    private val paymentStatusUpdateGateway = mock<PaymentStatusUpdateGateway>()
    private val stripeConnectGateway = mock<StripeConnectGateway>()
    private val eventPublisher = mock<ApplicationEventPublisher>()

    private val controller = StripeWebhookController(
        StripeProperties(webhookSecret = webhookSecret, connectWebhookSecret = connectWebhookSecret),
        paymentStatusUpdateGateway,
        stripeConnectGateway,
        eventPublisher,
    )

    @Test
    fun `older api version is different from the sdk pinned version`() {
        assertNotEquals(olderApiVersion.substringAfter('.'), Stripe.API_VERSION.substringAfter('.'))
    }

    @Test
    fun `payment_intent succeeded at older api version settles payment`() {
        val paymentRequestId = UUID.randomUUID()
        val payload = paymentIntentEvent("payment_intent.succeeded", paymentRequestId)

        val response = controller.receive(sign(payload, webhookSecret), payload)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(paymentStatusUpdateGateway).settle(eq(paymentRequestId), eq("stripe:evt_pi_1"), any())
        val captor = argumentCaptor<StripePaymentSettled>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals("pi_123", captor.firstValue.stripePaymentIntentId)
        assertEquals(1500L, captor.firstValue.grossAmountMinor)
        assertEquals("EUR", captor.firstValue.currency)
    }

    @Test
    fun `payment_intent failed at older api version fails payment`() {
        val paymentRequestId = UUID.randomUUID()
        val payload = paymentIntentEvent("payment_intent.payment_failed", paymentRequestId)

        val response = controller.receive(sign(payload, webhookSecret), payload)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(paymentStatusUpdateGateway).fail(eq(paymentRequestId), eq("stripe:evt_pi_1"), any())
    }

    @Test
    fun `connect account updated at older api version updates onboarding status`() {
        val payload = """
            {
              "id": "evt_acct_1",
              "object": "event",
              "api_version": "$olderApiVersion",
              "created": 1767225600,
              "type": "account.updated",
              "account": "acct_connected",
              "livemode": false,
              "data": {
                "object": {
                  "id": "acct_connected",
                  "object": "account",
                  "charges_enabled": true,
                  "details_submitted": true,
                  "capabilities": {
                    "card_payments": "active",
                    "pay_by_bank_payments": "active"
                  }
                }
              }
            }
        """.trimIndent()

        val response = controller.receiveConnect(sign(payload, connectWebhookSecret), payload)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(stripeConnectGateway).onAccountUpdated(
            connectedAccountId = "acct_connected",
            chargesEnabled = true,
            detailsSubmitted = true,
            payByBankEnabled = true,
        )
    }

    @Test
    fun `invalid signature is rejected`() {
        val payload = paymentIntentEvent("payment_intent.succeeded", UUID.randomUUID())

        val response = controller.receive(sign(payload, "whsec_wrong"), payload)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun paymentIntentEvent(type: String, paymentRequestId: UUID) = """
        {
          "id": "evt_pi_1",
          "object": "event",
          "api_version": "$olderApiVersion",
          "created": 1767225600,
          "type": "$type",
          "livemode": false,
          "data": {
            "object": {
              "id": "pi_123",
              "object": "payment_intent",
              "amount": 1500,
              "currency": "eur",
              "status": "succeeded",
              "metadata": { "paymentRequestId": "$paymentRequestId" }
            }
          }
        }
    """.trimIndent()

    private fun sign(payload: String, secret: String): String {
        val timestamp = Webhook.Util.getTimeNow()
        val signature = Webhook.Util.computeHmacSha256(secret, "$timestamp.$payload")
        return "t=$timestamp,v1=$signature"
    }
}
