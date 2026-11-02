package com.elegant.software.blitzpay.order.api

import org.springframework.modulith.NamedInterface
import java.util.UUID

/** Session cookie carrying the web storefront guest token (see `GuestToken`). */
const val STOREFRONT_GUEST_TOKEN_COOKIE = "guest_token"

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

    fun isMerchantOrder(orderId: String, merchantId: UUID, branchId: UUID? = null): Boolean

    fun findLastPaymentRequestId(orderId: String): UUID?

    /** True when [token] is the unexpired web storefront guest token issued for [orderId]. */
    fun isGuestTokenValid(orderId: String, token: String?): Boolean

    fun countPaymentAttempts(orderId: String): Long
}

data class OrderPaymentSummary(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val totalAmountMinor: Long,
    val currency: String,
)
