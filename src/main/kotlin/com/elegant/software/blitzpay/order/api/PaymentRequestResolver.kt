package com.elegant.software.blitzpay.order.api

import java.util.UUID

interface PaymentRequestResolver {
    fun findLastPaymentRequestId(orderId: String): UUID?
}
