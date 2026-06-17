package com.elegant.software.blitzpay.payments.push.api

import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import java.time.Instant
import java.util.UUID

data class PaymentStatusResponse(
    val paymentRequestId: UUID,
    val status: PaymentStatusCode,
    val terminal: Boolean,
    val lastEventAt: Instant?,
)