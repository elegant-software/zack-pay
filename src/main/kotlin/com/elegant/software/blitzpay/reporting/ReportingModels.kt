package com.elegant.software.blitzpay.reporting

import java.time.Instant
import java.util.UUID

data class SettlementSummaryResponse(
    val merchantId: UUID,
    val from: String,
    val to: String,
    val currency: String,
    val transactionCount: Int,
    val grossAmountMinor: Long,
    val processingFeeMinor: Long,
    val platformFeeMinor: Long,
    val netAmountMinor: Long,
    val byPaymentMethod: List<PaymentMethodBreakdown>,
)

data class PaymentMethodBreakdown(
    val paymentMethod: String,
    val transactionCount: Int,
    val grossAmountMinor: Long,
    val netAmountMinor: Long,
)

data class SettlementPageResponse(
    val content: List<SettlementRow>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class SettlementRow(
    val paymentRequestId: UUID,
    val orderId: String,
    val settledAt: Instant,
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
    val providerDashboardUrl: String?,
)

data class SettlementRefreshRequest(
    val merchantId: UUID,
    val paymentRequestIds: Set<UUID>,
)
