package com.elegant.software.blitzpay.order.repository

import com.elegant.software.blitzpay.order.domain.CreatorType
import com.elegant.software.blitzpay.order.domain.Order
import com.elegant.software.blitzpay.order.domain.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByOrderId(orderId: String): Order?

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

    fun findAllByAppointmentId(appointmentId: UUID): List<Order>
}
