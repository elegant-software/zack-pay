package com.elegant.software.blitzpay.payments.push.api

import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import java.time.Instant
import java.util.UUID

data class RecentPaymentSummary(
    val paymentRequestId: UUID,
    val status: PaymentStatusCode,
    val updatedAt: Instant,
    val orderId: String? = null,
    val amountMinorUnits: Long? = null,
    val currency: String? = null,
)
