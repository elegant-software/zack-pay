package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.order.api.PaymentSettlementData
import com.elegant.software.blitzpay.order.api.PaymentSettlementGateway
import com.elegant.software.blitzpay.order.api.PaymentSettlementRefreshCandidate
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.inbound.StripePaymentSettled
import com.elegant.software.blitzpay.payments.stripe.inbound.StripeSettlementClient
import com.elegant.software.blitzpay.payments.stripe.inbound.StripeSettlementEnrichmentListener
import com.elegant.software.blitzpay.payments.stripe.inbound.StripeSettlementRefreshService
import com.elegant.software.blitzpay.payments.stripe.inbound.stripeDashboardPaymentUrl
import com.stripe.model.BalanceTransaction
import com.stripe.model.Charge
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StripeSettlementEnrichmentListenerTest {
    private val settlementGateway = mock<PaymentSettlementGateway>()
    private val stripeClient = mock<StripeSettlementClient>()
    private val listener = StripeSettlementEnrichmentListener(
        StripeProperties(secretKey = "sk_test"),
        settlementGateway,
        stripeClient,
    )

    @Test
    fun `retrieves balance transaction by id instead of recording zero fee`() {
        val charge = Charge().apply {
            id = "ch_card"
            balanceTransaction = "txn_card"
        }
        val balanceTransaction = BalanceTransaction().apply {
            id = "txn_card"
            fee = 59L
            net = 941L
        }
        whenever(stripeClient.fetchLatestCharge(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(charge)
        whenever(stripeClient.fetchBalanceTransaction(org.mockito.kotlin.eq("txn_card"), org.mockito.kotlin.any()))
            .thenReturn(balanceTransaction)

        listener.on(event(grossAmountMinor = 1_000L))

        val settlement = argumentCaptor<PaymentSettlementData>()
        verify(settlementGateway).record(settlement.capture())
        assertEquals(59L, settlement.firstValue.processingFeeMinor)
        assertEquals(941L, settlement.firstValue.netAmountMinor)
        assertEquals("txn_card", settlement.firstValue.providerBalanceTxReference)
    }

    @Test
    fun `does not persist incomplete settlement when balance transaction is unavailable`() {
        val charge = Charge().apply {
            id = "ch_bank"
            balanceTransaction = "txn_bank"
        }
        whenever(stripeClient.fetchLatestCharge(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(charge)
        whenever(stripeClient.fetchBalanceTransaction(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(null)

        listener.on(event(grossAmountMinor = 2_000L))

        verify(settlementGateway, never()).record(org.mockito.kotlin.any())
    }

    @Test
    fun `excludes application fee from Stripe processing fee`() {
        val charge = Charge().apply {
            id = "ch_card"
            balanceTransaction = "txn_card"
        }
        val balanceTransaction = BalanceTransaction().apply {
            id = "txn_card"
            fee = 100L
            net = 900L
        }
        whenever(stripeClient.fetchLatestCharge(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(charge)
        whenever(stripeClient.fetchBalanceTransaction(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(balanceTransaction)

        listener.on(event(grossAmountMinor = 1_000L, applicationFeeAmountMinor = 25L))

        val settlement = argumentCaptor<PaymentSettlementData>()
        verify(settlementGateway).record(settlement.capture())
        assertEquals(75L, settlement.firstValue.processingFeeMinor)
        assertEquals(25L, settlement.firstValue.platformFeeMinor)
        assertEquals(900L, settlement.firstValue.netAmountMinor)
    }

    @Test
    fun `refresh updates availability and Stripe dashboard link from existing charge`() {
        val merchantId = UUID.randomUUID()
        val paymentRequestId = UUID.randomUUID()
        val stripeConnectGateway = mock<StripeConnectGateway>()
        val refreshService = StripeSettlementRefreshService(
            StripeProperties(secretKey = "sk_test", dashboardPlatformAccountId = "acct_platform"),
            stripeConnectGateway,
            settlementGateway,
            stripeClient,
        )
        val charge = Charge().apply {
            id = "ch_existing"
            paymentIntent = "pi_existing"
            balanceTransaction = "txn_existing"
            livemode = false
        }
        val balanceTransaction = BalanceTransaction().apply {
            id = "txn_existing"
            fee = 59L
            net = 941L
            availableOn = 1_782_864_000L
        }
        whenever(stripeConnectGateway.findConnectedAccountId(merchantId)).thenReturn("acct_connected")
        whenever(settlementGateway.findStripeRefreshCandidates(merchantId, setOf(paymentRequestId))).thenReturn(
            listOf(
                PaymentSettlementRefreshCandidate(
                    paymentRequestId = paymentRequestId,
                    merchantId = merchantId,
                    currency = "EUR",
                    grossAmountMinor = 1_000L,
                    platformFeeMinor = 0L,
                    providerPaymentReference = null,
                    providerChargeReference = "ch_existing",
                    settledAt = Instant.parse("2026-06-10T10:00:00Z"),
                )
            )
        )
        whenever(stripeClient.fetchCharge(org.mockito.kotlin.eq("ch_existing"), org.mockito.kotlin.any())).thenReturn(charge)
        whenever(stripeClient.fetchBalanceTransaction(org.mockito.kotlin.eq("txn_existing"), org.mockito.kotlin.any()))
            .thenReturn(balanceTransaction)
        whenever(settlementGateway.refresh(org.mockito.kotlin.any())).thenReturn(true)

        val result = refreshService.refresh(merchantId, setOf(paymentRequestId))

        assertEquals(1, result.refreshed)
        val settlement = argumentCaptor<PaymentSettlementData>()
        verify(settlementGateway).refresh(settlement.capture())
        assertEquals(Instant.ofEpochSecond(1_782_864_000L), settlement.firstValue.fundsAvailableAt)
        assertEquals("pi_existing", settlement.firstValue.providerPaymentReference)
        assertEquals(
            "https://dashboard.stripe.com/acct_platform/test/connect/accounts/acct_connected/payments/pi_existing?redirected=true",
            settlement.firstValue.providerDashboardUrl,
        )
    }

    @Test
    fun `Stripe dashboard link is absent without platform account configuration`() {
        assertNull(stripeDashboardPaymentUrl("", "acct_connected", "pi_existing", liveMode = false))
    }

    private fun event(
        grossAmountMinor: Long,
        applicationFeeAmountMinor: Long? = null,
    ) = StripePaymentSettled(
        paymentRequestId = UUID.randomUUID(),
        stripePaymentIntentId = "pi_test",
        stripeEventId = "evt_test",
        connectedAccountId = "acct_test",
        grossAmountMinor = grossAmountMinor,
        currency = "EUR",
        applicationFeeAmountMinor = applicationFeeAmountMinor,
        occurredAt = Instant.parse("2026-06-10T10:00:00Z"),
    )
}
