package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderPaymentSummary
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.outbound.StripeIntentResult
import com.elegant.software.blitzpay.payments.stripe.outbound.StripePaymentService
import com.elegant.software.blitzpay.payments.stripe.web.CreateIntentRequest
import com.elegant.software.blitzpay.payments.stripe.web.StripePaymentController
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

// Regression: ORD-9D0014F0F940 was charged €4.90 (first product's price / client amount) instead of the €39.05 order total.
class StripePaymentControllerAmountTest {

    private val stripePaymentService = mock<StripePaymentService>()
    private val credentialResolver = mock<MerchantCredentialResolver>()
    private val stripeConnectGateway = mock<StripeConnectGateway>()
    private val orderGateway = mock<OrderGateway>()
    private val statusGateway = mock<PaymentStatusInitializationGateway>()
    private val controller = StripePaymentController(
        stripePaymentService, credentialResolver, stripeConnectGateway, StripeProperties(), orderGateway, statusGateway,
    )

    @Test
    fun `create intent charges the order total, ignoring client amount, productId and currency`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        whenever(orderGateway.assertPayable("ORD-1")).thenReturn(OrderPaymentSummary("ORD-1", merchantId, branchId, 3905, "EUR"))
        whenever(credentialResolver.resolveBranch(merchantId, branchId, productId)).thenReturn(branchId)
        whenever(credentialResolver.resolveProductPrice(productId)).thenReturn(BigDecimal("4.90"))
        whenever(stripeConnectGateway.findConnectedAccountId(merchantId)).thenReturn("acct_1")
        whenever(stripeConnectGateway.isChargesEnabled(merchantId)).thenReturn(true)
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any()))
            .thenReturn(StripeIntentResult("secret", "pi_1", "acct_1"))

        val response = controller.createIntent(
            CreateIntentRequest(amount = 4.9, currency = "usd", orderId = "ORD-1", merchantId = merchantId, branchId = branchId, productId = productId),
        )

        assertEquals(200, response.statusCode.value())
        verify(stripePaymentService).createIntent(eq(39.05), eq("eur"), eq("acct_1"), eq(merchantId), eq(branchId), eq("ORD-1"), any(), eq(productId), eq("card"))
        verify(statusGateway).initialize(any(), anyOrNull(), eq("ORD-1"), eq(3905L), eq("EUR"))
    }
}
