package com.elegant.software.blitzpay.order.persistence.model

enum class OrderStatus {
    CREATED,
    PAYMENT_INITIATED,
    PAID,
    FAILED,
    CANCELLED;

    fun isTerminal(): Boolean = this == PAID

    val paymentRetryAllowed: Boolean
        get() = this == CREATED || this == FAILED || this == CANCELLED
}
