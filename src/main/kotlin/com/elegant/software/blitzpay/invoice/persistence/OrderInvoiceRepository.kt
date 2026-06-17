package com.elegant.software.blitzpay.invoice.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderInvoiceRepository : JpaRepository<OrderInvoice, UUID> {
    fun existsByOrderId(orderId: String): Boolean
    fun findByOrderId(orderId: String): OrderInvoice?
}
