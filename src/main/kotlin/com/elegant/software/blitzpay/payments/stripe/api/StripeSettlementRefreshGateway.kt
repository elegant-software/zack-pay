package com.elegant.software.blitzpay.payments.stripe.api

import org.springframework.modulith.NamedInterface
import java.util.UUID

@NamedInterface("StripeSettlementRefreshGateway")
interface StripeSettlementRefreshGateway {
    fun refresh(merchantId: UUID, paymentRequestIds: Set<UUID>): StripeSettlementRefreshResult
}

data class StripeSettlementRefreshResult(
    val requested: Int,
    val refreshed: Int,
    val skipped: Int,
    val failed: Int,
    val results: List<StripeSettlementRefreshItem>,
)

data class StripeSettlementRefreshItem(
    val paymentRequestId: UUID,
    val status: String,
    val message: String?,
)
