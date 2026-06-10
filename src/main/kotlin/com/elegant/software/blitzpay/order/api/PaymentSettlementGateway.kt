package com.elegant.software.blitzpay.order.api

import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

@NamedInterface("PaymentSettlementGateway")
interface PaymentSettlementGateway {
    fun record(data: PaymentSettlementData)
    fun findStripeRefreshCandidates(merchantId: UUID, paymentRequestIds: Set<UUID>): List<PaymentSettlementRefreshCandidate>
    fun refresh(data: PaymentSettlementData): Boolean
}

data class PaymentSettlementRefreshCandidate(
    val paymentRequestId: UUID,
    val merchantId: UUID,
    val currency: String,
    val grossAmountMinor: Long,
    val platformFeeMinor: Long,
    val providerPaymentReference: String?,
    val providerChargeReference: String?,
    val settledAt: Instant,
)

data class PaymentSettlementData(
    val paymentRequestId: UUID,
    val provider: String,
    val currency: String,
    val grossAmountMinor: Long,
    val processingFeeMinor: Long,
    val platformFeeMinor: Long,
    val netAmountMinor: Long,
    val paymentMethod: String,
    val paymentMethodDetail: String?,
    val fundsAvailableAt: Instant?,
    val providerPaymentReference: String?,
    val providerChargeReference: String?,
    val providerBalanceTxReference: String?,
    val providerDashboardUrl: String?,
    val settledAt: Instant,
)
