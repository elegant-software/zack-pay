package com.elegant.software.blitzpay.order.persistence.repository

import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByOrderId(orderId: String): Order?

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    fun findByIdForPreparationUpdate(id: UUID): Order?

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :code")
    fun lockForItemMutation(code: String): Order?

    fun findByMerchantBranchIdAndCreatedAtBetween(
        branchId: UUID,
        from: Instant,
        to: Instant,
    ): List<Order>

    fun findByMerchantBranchIdAndStatusAndCreatedAtBetween(
        branchId: UUID,
        status: OrderStatus,
        from: Instant,
        to: Instant,
    ): List<Order>

    fun findByCreatedByIdAndCreatorTypeAndCreatedAtAfter(
        createdById: String,
        creatorType: CreatorType,
        after: Instant,
    ): List<Order>

    @Query("""
        SELECT o FROM Order o
        WHERE o.createdAt > :after
          AND ((o.createdById = :shopperId AND o.creatorType = 'SHOPPER')
               OR o.targetShopperId = :shopperId)
        ORDER BY o.createdAt DESC
    """)
    fun findShopperOrders(shopperId: String, after: Instant): List<Order>

    @Query("""
        SELECT DISTINCT o.createdById FROM Order o
        WHERE o.merchantBranchId = :branchId
          AND o.creatorType = 'SHOPPER'
    """)
    fun findDistinctShopperIdsByBranch(branchId: UUID): List<String>

    // ponytail: scans the whole lookback window and returns every id in it; fine up to ~tens of
    // thousands of orders per window. Beyond that, page it or track invoice state on the order.
    // Guests without a consented invoice email get no invoice, so they aren't listed.
    @Query("""
        SELECT o.orderId FROM Order o
        WHERE (o.status = 'PAID' AND o.paidAt > :since
               AND NOT (o.creatorType = 'GUEST' AND o.invoiceEmail IS NULL))
           OR (o.targetShopperId IS NOT NULL AND o.createdAt > :since)
        ORDER BY o.createdAt
    """)
    fun findInvoiceableOrderIds(since: Instant): List<String>

    fun findAllByAppointmentId(appointmentId: UUID): List<Order>

    fun findAllByTableIdInAndCreatedAtAfterAndStatusNot(
        tableIds: Collection<UUID>,
        after: Instant,
        status: OrderStatus,
    ): List<Order>

    fun existsByTableId(tableId: UUID): Boolean
}
