package com.elegant.software.blitzpay.order.api

enum class PaymentStatusCode {
    PENDING,
    EXECUTED,
    SETTLED,
    FAILED,
    EXPIRED;

    fun isTerminal(): Boolean = this == SETTLED || this == FAILED || this == EXPIRED

    fun rank(): Int = when (this) {
        PENDING -> 0
        EXECUTED -> 1
        SETTLED -> 2
        FAILED -> 2
        EXPIRED -> 2
    }
}
