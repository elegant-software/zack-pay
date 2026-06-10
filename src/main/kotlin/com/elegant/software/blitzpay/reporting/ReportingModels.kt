package com.elegant.software.blitzpay.reporting

import java.time.Instant
import java.util.UUID

// ── Dashboard models ──────────────────────────────────────────────────────────

data class PlatformKpiReport(
    val from: String,
    val to: String,
    val merchantId: UUID?,
    val revenue: RevenueKpi,
    val orders: OrderKpi,
    val appointments: AppointmentKpi,
    val paymentMethodFrequency: List<PaymentMethodFrequency>,
    val merchantCounts: MerchantCountKpi?,
    val branchCounts: BranchCountKpi?,
)

data class MerchantKpiReport(
    val merchantId: UUID,
    val branchId: UUID?,
    val from: String,
    val to: String,
    val revenue: RevenueKpi,
    val orders: OrderKpi,
    val appointments: AppointmentKpi,
    val paymentMethodFrequency: List<PaymentMethodFrequency>,
)

data class RevenueKpi(
    val currency: String,
    val grossAmountMinor: Long,
    val processingFeeMinor: Long,
    val platformFeeMinor: Long,
    val netAmountMinor: Long,
    val settledTransactionCount: Int,
)

data class OrderKpi(
    val paidCount: Long,
    val pendingCount: Long,
    val failedOrCancelledCount: Long,
    val byOrderType: List<OrderTypeCount>,
)

data class OrderTypeCount(
    val orderType: String,
    val count: Long,
)

data class AppointmentKpi(
    val bookedCount: Long,
    val completedCount: Long,
    val cancelledCount: Long,
)

data class PaymentMethodFrequency(
    val paymentMethod: String,
    val transactionCount: Int,
    val sharePercent: Double,
)

data class MerchantCountKpi(
    val totalCount: Long,
    val activeCount: Long,
    val newInPeriodCount: Long,
)

data class BranchCountKpi(
    val totalCount: Long,
    val activeCount: Long,
    val newInPeriodCount: Long,
)

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
