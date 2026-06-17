package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.web.ConnectPaymentFlow
import com.elegant.software.blitzpay.payments.stripe.web.StripeConnectController
import com.elegant.software.blitzpay.payments.stripe.web.StripeConnectService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertEquals

class StripeConnectControllerTest {

    private val stripeConnectService = mock<StripeConnectService>()
    private val controller = StripeConnectController(
        stripeConnectService = stripeConnectService,
        stripeProperties = StripeProperties(),
    )

    @Test
    fun `status includes pay by bank flow without stripe connect account`() {
        val merchantId = UUID.randomUUID()
        whenever(stripeConnectService.findConnectedAccountId(merchantId)).thenReturn(null)
        whenever(stripeConnectService.isPayByBankEnabled(merchantId)).thenReturn(true)

        val response = controller.getStatus(merchantId)

        assertEquals(false, response.body?.enabledPaymentFlows?.contains(ConnectPaymentFlow.PAY_BY_BANK))
        assertEquals(false, response.body?.enabledPaymentFlows?.contains(ConnectPaymentFlow.CARD))
        assertEquals(false, response.body?.connected)
        assertEquals(false, response.body?.chargesEnabled)
        assertEquals(false, response.body?.detailsSubmitted)
    }

    @Test
    fun `status includes both card and pay by bank flows when available`() {
        val merchantId = UUID.randomUUID()
        whenever(stripeConnectService.findConnectedAccountId(merchantId)).thenReturn("acct_123")
        whenever(stripeConnectService.isChargesEnabled(merchantId)).thenReturn(true)
        whenever(stripeConnectService.isDetailsSubmitted(merchantId)).thenReturn(true)
        whenever(stripeConnectService.isPayByBankEnabled(merchantId)).thenReturn(true)

        val response = controller.getStatus(merchantId)

        assertEquals(setOf(ConnectPaymentFlow.CARD, ConnectPaymentFlow.PAY_BY_BANK), response.body?.enabledPaymentFlows)
        assertEquals(true, response.body?.connected)
        assertEquals(true, response.body?.chargesEnabled)
        assertEquals(true, response.body?.detailsSubmitted)
    }
}
