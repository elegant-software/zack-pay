package com.elegant.software.blitzpay.order.api

import java.time.Instant
import java.util.UUID

data class PaymentStatusChanged(
    val paymentRequestId: UUID,
    val orderId: String?,
    val newStatus: PaymentStatusCode,
    val previousStatus: PaymentStatusCode?,
    val occurredAt: Instant,
    val sourceEventId: String,
)
