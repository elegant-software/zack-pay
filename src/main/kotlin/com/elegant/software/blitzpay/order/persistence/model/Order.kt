package com.elegant.software.blitzpay.order.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "order_orders", schema = "blitzpay")
class Order(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    val orderId: String,

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(name = "merchant_branch_id")
    val merchantBranchId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 32)
    val orderType: OrderType = OrderType.PRE_ORDER,

    @Column(name = "uses_deferred_payment", nullable = false)
    val usesDeferredPayment: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OrderStatus = OrderStatus.CREATED,

    @Enumerated(EnumType.STRING)
    @Column(name = "creator_type", nullable = false, length = 16)
    val creatorType: CreatorType,

    @Column(name = "created_by_id", nullable = false, length = 255)
    val createdById: String,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "total_amount_minor", nullable = false)
    var totalAmountMinor: Long,

    @Column(name = "item_count", nullable = false)
    var itemCount: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @Column(name = "last_payment_request_id")
    var lastPaymentRequestId: UUID? = null,

    @Column(name = "last_payment_provider", length = 32)
    var lastPaymentProvider: String? = null,

    @Column(name = "payment_source", length = 32)
    var paymentSource: String? = null,

    @Column(name = "checkout_payment_method", length = 32)
    var checkoutPaymentMethod: String? = null,

    @Column(name = "settlement_note", length = 2000)
    var settlementNote: String? = null,

    @Column(name = "target_shopper_id", length = 512)
    val targetShopperId: String? = null,

    @Column(name = "due_date")
    val dueDate: LocalDate? = null,

    @Column(name = "appointment_id")
    val appointmentId: UUID? = null,

    @Column(name = "buyer_contact_name", length = 255)
    val buyerContactName: String? = null,

    @Column(name = "buyer_contact_street", length = 255)
    val buyerContactStreet: String? = null,

    @Column(name = "buyer_contact_zip", length = 32)
    val buyerContactZip: String? = null,

    @Column(name = "buyer_contact_city", length = 128)
    val buyerContactCity: String? = null,

    @Column(name = "buyer_contact_country", length = 64)
    val buyerContactCountry: String? = null,

    @Column(name = "guest_token_hash", length = 64)
    val guestTokenHash: String? = null,

    @Column(name = "guest_token_expires_at")
    val guestTokenExpiresAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "preparation_status", nullable = false, length = 16)
    var preparationStatus: PreparationStatus = PreparationStatus.RECEIVED,

    /** Short per-Branch, per-day number; null for orders created before Pickup Numbers existed or without a Branch. */
    @Column(name = "pickup_number", updatable = false)
    val pickupNumber: Int? = null,
) {
    fun advancePreparation(next: PreparationStatus, at: Instant = Instant.now()) {
        require(preparationStatus.canAdvanceTo(next)) {
            "Preparation status cannot change from $preparationStatus to $next"
        }
        preparationStatus = next
        updatedAt = at
    }

    fun markPaymentInProgress(paymentRequestId: UUID, provider: String, at: Instant = Instant.now()) {
        require(status != OrderStatus.PAID) { "Order is already paid: $orderId" }
        if (status == OrderStatus.CREATED || status == OrderStatus.FAILED || status == OrderStatus.CANCELLED) {
            status = OrderStatus.PAYMENT_INITIATED
        }
        lastPaymentRequestId = paymentRequestId
        lastPaymentProvider = provider
        updatedAt = at
    }

    fun applyPaymentUpdate(
        nextStatus: OrderStatus,
        paymentRequestId: UUID,
        provider: String?,
        at: Instant,
    ): Boolean {
        if (status == OrderStatus.PAID) return false
        val allowed = when (status) {
            OrderStatus.CREATED -> setOf(OrderStatus.PAYMENT_INITIATED, OrderStatus.PAID, OrderStatus.FAILED, OrderStatus.CANCELLED)
            OrderStatus.PAYMENT_INITIATED -> setOf(OrderStatus.PAID, OrderStatus.FAILED, OrderStatus.CANCELLED)
            OrderStatus.FAILED -> setOf(OrderStatus.PAYMENT_INITIATED, OrderStatus.CANCELLED)
            OrderStatus.CANCELLED -> setOf(OrderStatus.PAYMENT_INITIATED)
            OrderStatus.PAID -> emptySet()
        }
        if (nextStatus !in allowed) return false
        status = nextStatus
        lastPaymentRequestId = paymentRequestId
        lastPaymentProvider = provider ?: lastPaymentProvider
        if (nextStatus == OrderStatus.PAID) {
            paidAt = at
            paymentSource = "APP_PAYMENT"
        }
        updatedAt = at
        return true
    }

    fun manualSettle(note: String?, by: String, at: Instant = Instant.now()) {
        require(status == OrderStatus.CREATED || status == OrderStatus.FAILED) {
            "Order cannot be manually settled in status: $status"
        }
        status = OrderStatus.PAID
        paymentSource = "MANUAL_SETTLEMENT"
        settlementNote = note
        paidAt = at
        updatedAt = at
    }

    fun recalculateTotals(activeItems: List<OrderItem>) {
        totalAmountMinor = activeItems.sumOf { it.effectiveUnitPriceMinor * it.quantity }
        itemCount = activeItems.sumOf { it.quantity }
        updatedAt = Instant.now()
    }

    companion object {
        fun nextOrderId(): String =
            "ORD-" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
    }
}
