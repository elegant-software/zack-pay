package com.elegant.software.blitzpay.invoice.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "order_invoices", schema = "blitzpay")
class OrderInvoice(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "order_id_fk", nullable = false, updatable = false)
    val orderIdFk: UUID,

    @Column(name = "order_id", nullable = false, unique = true, updatable = false, length = 64)
    val orderId: String,

    @Column(name = "s3_key", nullable = false, updatable = false, length = 512)
    val s3Key: String,

    @Column(name = "generated_at", nullable = false, updatable = false)
    val generatedAt: Instant,

    @Column(name = "delivery_status", nullable = false, length = 16)
    var deliveryStatus: String = DeliveryStatus.PENDING,
) {
    object DeliveryStatus {
        const val PENDING = "PENDING"
        const val SENT = "SENT"
    }
}
