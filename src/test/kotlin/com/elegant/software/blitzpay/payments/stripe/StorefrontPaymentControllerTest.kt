package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderPaymentSummary
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.outbound.StripeCheckoutSessionResult
import com.elegant.software.blitzpay.payments.stripe.outbound.StripeIntentResult
import com.elegant.software.blitzpay.payments.stripe.outbound.StripePaymentService
import com.elegant.software.blitzpay.payments.stripe.web.StorefrontPaymentController
import com.elegant.software.blitzpay.payments.stripe.web.StorefrontPaymentMethod
import com.elegant.software.blitzpay.payments.stripe.web.StorefrontPaymentRequest
import com.elegant.software.blitzpay.payments.stripe.web.StorefrontPaymentResponse
import com.elegant.software.blitzpay.payments.stripe.web.StripeCheckoutService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class StorefrontPaymentControllerTest {
    private val stripePaymentService = mock<StripePaymentService>()
    private val credentialResolver = mock<MerchantCredentialResolver>()
    private val stripeConnectGateway = mock<StripeConnectGateway>()
    private val orderGateway = mock<OrderGateway>()
    private val statusGateway = mock<PaymentStatusInitializationGateway>()
    private val properties = StripeProperties(publishableKey = "pk_test", storefrontUrl = "https://shop.example/")
    private val controller = StorefrontPaymentController(
        StripeCheckoutService(stripePaymentService, credentialResolver, stripeConnectGateway, properties, orderGateway, statusGateway),
        orderGateway,
        properties,
    )
    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    @BeforeEach
    fun stubOrder() {
        whenever(orderGateway.assertPayable("ORD-1")).thenReturn(OrderPaymentSummary("ORD-1", merchantId, branchId, 3905, "EUR"))
        whenever(credentialResolver.resolveBranch(merchantId, branchId, null)).thenReturn(branchId)
        whenever(stripeConnectGateway.findConnectedAccountId(merchantId)).thenReturn("acct_1")
        whenever(stripeConnectGateway.isChargesEnabled(merchantId)).thenReturn(true)
        whenever(stripeConnectGateway.isPayByBankEnabled(merchantId)).thenReturn(true)
        whenever(stripeConnectGateway.getDetails(merchantId)).thenReturn(
            MerchantDetailsResponse(merchantId = merchantId, merchantName = "Cafe", businessType = "RESTAURANT", operatingCountry = "DE", lastUpdatedAt = Instant.EPOCH),
        )
    }

    @Test
    fun `rejects a missing or wrong guest token without touching Stripe`() {
        whenever(orderGateway.isGuestTokenValid("ORD-1", "wrong")).thenReturn(false)

        val response = controller.pay("ORD-1", StorefrontPaymentRequest(StorefrontPaymentMethod.CARD), "wrong")

        assertEquals(404, response.statusCode.value())
        verifyNoInteractions(stripePaymentService, statusGateway)
    }

    @Test
    fun `caps payment attempts per order`() {
        whenever(orderGateway.isGuestTokenValid("ORD-1", "tok")).thenReturn(true)
        whenever(orderGateway.countPaymentAttempts("ORD-1")).thenReturn(5)

        val response = controller.pay("ORD-1", StorefrontPaymentRequest(StorefrontPaymentMethod.CARD), "tok")

        assertEquals(429, response.statusCode.value())
        verifyNoInteractions(stripePaymentService)
    }

    @Test
    fun `card charges the order total on the order's merchant`() {
        whenever(orderGateway.isGuestTokenValid("ORD-1", "tok")).thenReturn(true)
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any()))
            .thenReturn(StripeIntentResult("secret", "pi_1", "acct_1"))

        val response = controller.pay("ORD-1", StorefrontPaymentRequest(StorefrontPaymentMethod.CARD), "tok")

        assertEquals(200, response.statusCode.value())
        assertEquals(StorefrontPaymentResponse(StorefrontPaymentMethod.CARD, "acct_1", "secret", "pk_test"), response.body)
        verify(stripePaymentService).createIntent(eq(39.05), eq("eur"), eq("acct_1"), eq(merchantId), eq(branchId), eq("ORD-1"), any(), anyOrNull(), eq("card"))
    }

    @Test
    fun `pay by bank redirects only to the configured storefront`() {
        whenever(orderGateway.isGuestTokenValid("ORD-1", "tok")).thenReturn(true)
        whenever(stripePaymentService.createPayByBankCheckoutSession(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(StripeCheckoutSessionResult("https://checkout.stripe.com/c/1", "cs_1", "acct_1"))

        val response = controller.pay("ORD-1", StorefrontPaymentRequest(StorefrontPaymentMethod.PAY_BY_BANK), "tok")

        assertEquals(200, response.statusCode.value())
        val url = "https://shop.example/order/ORD-1/confirmation"
        verify(stripePaymentService).createPayByBankCheckoutSession(eq(39.05), eq("eur"), eq("acct_1"), eq(merchantId), eq(branchId), eq("ORD-1"), any(), anyOrNull(), eq(url), eq(url), eq("CAFE"))
    }
}
