package com.elegant.software.blitzpay.order.api

import org.springframework.modulith.NamedInterface
import java.time.Instant
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

    /** Order ids that need an invoice: paid after [since], or merchant-issued invoice orders created after [since]. Oldest first. */
    fun findInvoiceableOrderIds(since: Instant): List<String>

    /** Invoice data for a paid order or an invoice order; null if the order is unknown or neither. */
    fun findInvoiceSource(orderId: String): OrderInvoiceSource?

    /** Drops a guest's invoice email once the invoice is sent: its only purpose is fulfilled. No-op for other orders. */
    fun clearGuestInvoiceEmail(orderId: String)
}

data class OrderPaymentSummary(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val totalAmountMinor: Long,
    val currency: String,
)
