package com.elegant.software.blitzpay.order.persistence.repository

import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderItemRepository : JpaRepository<OrderItem, UUID> {
    fun findAllByOrderIdFk(orderIdFk: UUID): List<OrderItem>
    fun deleteAllByOrderIdFk(orderIdFk: UUID)
}
