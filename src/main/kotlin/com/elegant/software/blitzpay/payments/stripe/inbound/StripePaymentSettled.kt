package com.elegant.software.blitzpay.payments.stripe.internal

import java.time.Instant
import java.util.UUID

data class StripePaymentSettled(
    val paymentRequestId: UUID,
    val stripePaymentIntentId: String,
    val stripeEventId: String,
    val connectedAccountId: String?,
    val grossAmountMinor: Long,
    val currency: String,
    val applicationFeeAmountMinor: Long?,
    val occurredAt: Instant,
)
