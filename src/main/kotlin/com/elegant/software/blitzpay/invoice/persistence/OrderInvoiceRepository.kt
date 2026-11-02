package com.elegant.software.blitzpay.invoice.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface OrderInvoiceRepository : JpaRepository<OrderInvoice, UUID> {
    fun existsByOrderId(orderId: String): Boolean
    fun findByOrderId(orderId: String): OrderInvoice?

    @Query("SELECT i.orderId FROM OrderInvoice i WHERE i.generatedAt > :since")
    fun findOrderIdsGeneratedAfter(since: Instant): List<String>

    fun findByDeliveryStatusOrderByGeneratedAt(deliveryStatus: String, pageable: Pageable): List<OrderInvoice>

    /** Transaction-scoped Postgres advisory lock; released automatically on commit or rollback. */
    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    fun tryAdvisoryXactLock(key: Long): Boolean
}
