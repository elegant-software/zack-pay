package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.api.BranchTerminalLocation
import com.elegant.software.blitzpay.merchant.api.MerchantAccessGateway
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderPaymentAttempt
import com.elegant.software.blitzpay.order.api.OrderPaymentSummary
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.outbound.StripeIntentResult
import com.elegant.software.blitzpay.payments.stripe.outbound.StripePaymentService
import com.elegant.software.blitzpay.payments.stripe.web.ConnectionTokenRequest
import com.elegant.software.blitzpay.payments.stripe.web.StripeCheckoutService
import com.elegant.software.blitzpay.payments.stripe.web.StripeCheckoutTarget
import com.elegant.software.blitzpay.payments.stripe.web.StripeTerminalController
import com.stripe.model.PaymentIntent
import com.stripe.model.terminal.Reader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StripeTerminalPaymentTest {

    private val stripePaymentService = mock<StripePaymentService>()
    private val credentialResolver = mock<MerchantCredentialResolver>()
    private val stripeConnectGateway = mock<StripeConnectGateway>()
    private val orderGateway = mock<OrderGateway>()
    private val statusGateway = mock<PaymentStatusInitializationGateway>()
    private val checkoutService = StripeCheckoutService(
        stripePaymentService, credentialResolver, stripeConnectGateway, StripeProperties(publishableKey = "pk_test"), orderGateway, statusGateway,
    )
    private val merchantAccess: MerchantAccessGateway = mock { on { isManagerOrPlatformAdmin(any(), anyOrNull()) } doReturn true }
    private val terminalController = StripeTerminalController(stripeConnectGateway, StripeProperties(), stripePaymentService, merchantAccess)

    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val target = StripeCheckoutTarget("ORD-1", merchantId, branchId)

    @BeforeEach
    fun setUp() {
        whenever(orderGateway.assertPayable("ORD-1")).thenReturn(OrderPaymentSummary("ORD-1", merchantId, branchId, 1250, "EUR"))
        whenever(credentialResolver.resolveBranch(merchantId, branchId, null)).thenReturn(branchId)
        whenever(stripeConnectGateway.findConnectedAccountId(merchantId)).thenReturn("acct_1")
        whenever(stripeConnectGateway.isChargesEnabled(merchantId)).thenReturn(true)
        whenever(stripeConnectGateway.isTapToPayEnabled(merchantId)).thenReturn(true)
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any()))
            .thenReturn(StripeIntentResult("pi_new_secret", "pi_new", "acct_1"))
    }

    @Test
    fun `retry returns the open terminal PaymentIntent without a new attempt`() {
        val paymentRequestId = UUID.randomUUID()
        givenLastTerminalIntent(paymentRequestId, "requires_payment_method")

        val response = checkoutService.createIntent(target, "card_present")

        assertEquals(paymentRequestId, response.paymentRequestId)
        assertEquals("pi_open", response.paymentIntent)
        assertEquals("pi_open_secret", response.clientSecret)
        verify(stripePaymentService, never()).createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any())
        verify(orderGateway, never()).linkPaymentAttempt(any(), any(), any(), anyOrNull())
        verify(statusGateway, never()).initialize(any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun `succeeded terminal PaymentIntent is rejected with ORDER_ALREADY_PAID`() {
        givenLastTerminalIntent(UUID.randomUUID(), "succeeded")

        val ex = assertThrows<ApiException> { checkoutService.createIntent(target, "card_present") }

        assertEquals(HttpStatus.CONFLICT, ex.status)
        assertEquals("ORDER_ALREADY_PAID", ex.code)
        verify(stripePaymentService, never()).createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `canceled terminal PaymentIntent leads to a new one linked to the new attempt`() {
        val oldPaymentRequestId = UUID.randomUUID()
        givenLastTerminalIntent(oldPaymentRequestId, "canceled")

        val response = checkoutService.createIntent(target, "card_present")

        assertNotEquals(oldPaymentRequestId, response.paymentRequestId)
        assertEquals("pi_new", response.paymentIntent)
        verify(orderGateway).linkPaymentAttempt(eq("ORD-1"), eq(response.paymentRequestId), eq("STRIPE_TERMINAL"), eq("pi_new"))
    }

    @Test
    fun `terminal PaymentIntent for a different amount is not reused`() {
        givenLastTerminalIntent(UUID.randomUUID(), "requires_payment_method", amount = 900L)

        val response = checkoutService.createIntent(target, "card_present")

        assertEquals("pi_new", response.paymentIntent)
        verify(stripePaymentService).cancelIntent("pi_open", "acct_1")
    }

    @Test
    fun `terminal PaymentIntent for a different amount mid-collection is rejected, never replaced`() {
        givenLastTerminalIntent(UUID.randomUUID(), "processing", amount = 900L)

        val ex = assertThrows<ApiException> { checkoutService.createIntent(target, "card_present") }

        assertEquals(HttpStatus.CONFLICT, ex.status)
        assertEquals("PAYMENT_IN_PROGRESS", ex.code)
        verify(stripePaymentService, never()).cancelIntent(any(), any())
    }

    @Test
    fun `terminal payments not enabled is rejected with 403`() {
        whenever(stripeConnectGateway.isTapToPayEnabled(merchantId)).thenReturn(false)

        val ex = assertThrows<ApiException> { checkoutService.createIntent(target, "card_present") }

        assertEquals(HttpStatus.FORBIDDEN, ex.status)
        assertEquals("Terminal payments are not enabled for this merchant", ex.message)
    }

    @Test
    fun `readers are listed per branch location with branch name`() {
        val otherBranchId = UUID.randomUUID()
        whenever(stripeConnectGateway.findTerminalLocations(merchantId)).thenReturn(
            listOf(BranchTerminalLocation(branchId, "Hauptfiliale", "tml_1"), BranchTerminalLocation(otherBranchId, "Bahnhof", "tml_2")),
        )
        whenever(stripePaymentService.listReaders("tml_1", "acct_1")).thenReturn(
            listOf(Reader().apply {
                id = "tmr_1"; label = "WisePad 3 Theke"; deviceType = "bbpos_wisepad3"; serialNumber = "WSC5123"
                deviceSwVersion = "4.01.07.00_DE"; status = "online"; lastSeenAt = 1_794_219_300_000L
            }),
        )

        val readers = terminalController.readers(merchantId, branchId, null)

        assertEquals(1, readers.size)
        val reader = readers.single()
        assertEquals("tmr_1", reader.id)
        assertEquals(branchId, reader.branchId)
        assertEquals("Hauptfiliale", reader.branchName)
        assertEquals("WisePad 3 Theke", reader.label)
        assertEquals("bbpos_wisepad3", reader.deviceType)
        assertEquals("WSC5123", reader.serialNumber)
        assertEquals("4.01.07.00_DE", reader.softwareVersion)
        assertEquals("online", reader.status)
        assertEquals(Instant.parse("2026-11-09T10:15:00Z"), reader.lastSeenAt)
        verify(stripePaymentService, never()).listReaders(eq("tml_2"), any())
    }

    @Test
    fun `no readers when terminal payments are not enabled or no connected account`() {
        whenever(stripeConnectGateway.isTapToPayEnabled(merchantId)).thenReturn(false)
        assertTrue(terminalController.readers(merchantId, null, null).isEmpty())

        whenever(stripeConnectGateway.isTapToPayEnabled(merchantId)).thenReturn(true)
        whenever(stripeConnectGateway.findConnectedAccountId(merchantId)).thenReturn(null)
        assertTrue(terminalController.readers(merchantId, null, null).isEmpty())

        verify(stripePaymentService, never()).listReaders(any(), any())
    }

    @Test
    fun `readers are refused to callers who are not a Merchant Manager or Platform Admin`() {
        whenever(merchantAccess.isManagerOrPlatformAdmin(merchantId, null)).thenReturn(false)

        val ex = assertThrows<ApiException> { terminalController.readers(merchantId, null, null) }

        assertEquals(HttpStatus.FORBIDDEN, ex.status)
        verify(stripePaymentService, never()).listReaders(any(), any())
    }

    @Test
    fun `connection token is refused to callers who do not work at the branch`() {
        whenever(merchantAccess.worksAtBranch(merchantId, branchId, null)).thenReturn(false)

        val ex = assertThrows<ApiException> { terminalController.connectionToken(ConnectionTokenRequest(merchantId, branchId), null) }

        assertEquals(HttpStatus.FORBIDDEN, ex.status)
    }

    private fun givenLastTerminalIntent(paymentRequestId: UUID, status: String, amount: Long = 1250L) {
        whenever(orderGateway.findLastPaymentAttempt("ORD-1")).thenReturn(OrderPaymentAttempt(paymentRequestId, "STRIPE_TERMINAL", "pi_open"))
        whenever(stripePaymentService.retrieveIntent("pi_open", "acct_1")).thenReturn(
            PaymentIntent().apply {
                id = "pi_open"
                this.status = status
                clientSecret = "pi_open_secret"
                this.amount = amount
                currency = "eur"
            },
        )
    }
}
