package com.elegant.software.blitzpay.order.api

import org.springframework.modulith.NamedInterface
import java.util.UUID

@NamedInterface("OrderGateway")
interface OrderGateway {
    fun assertPayable(orderId: String): OrderPaymentSummary

    fun linkPaymentAttempt(
        orderId: String,
        paymentRequestId: UUID,
        provider: String,
        providerReference: String? = null,
    ): OrderPaymentSummary

    fun isShopperOrder(orderId: String, shopperId: String): Boolean
}

data class OrderPaymentSummary(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val totalAmountMinor: Long,
    val currency: String,
)
