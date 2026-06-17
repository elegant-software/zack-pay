package com.elegant.software.blitzpay.order.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "order_payment_attempts", schema = "blitzpay")
class PaymentAttempt(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "order_id_fk", nullable = false, updatable = false)
    val orderIdFk: UUID,

    @Column(name = "order_id", nullable = false, updatable = false, length = 64)
    val orderId: String,

    @Column(name = "payment_request_id", nullable = false, unique = true, updatable = false)
    val paymentRequestId: UUID,

    @Column(nullable = false, length = 32)
    var provider: String,

    @Column(name = "provider_reference", length = 255)
    var providerReference: String? = null,

    @Column(nullable = false, length = 32)
    var status: String = "PENDING",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    fun update(providerReference: String? = this.providerReference, status: String = this.status, at: Instant = Instant.now()) {
        this.providerReference = providerReference ?: this.providerReference
        this.status = status
        this.updatedAt = at
    }
}
