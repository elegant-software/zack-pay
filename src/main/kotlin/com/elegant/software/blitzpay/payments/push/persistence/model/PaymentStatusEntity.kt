package com.elegant.software.blitzpay.payments.push.persistence.model

import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "push_payment_status")
class PaymentStatusEntity(
    @Id
    @Column(name = "payment_request_id", nullable = false)
    var paymentRequestId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 32)
    var currentStatus: PaymentStatusCode,

    @Column(name = "last_event_id", length = 128)
    var lastEventId: String? = null,

    @Column(name = "last_event_at")
    var lastEventAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "payer_ref", length = 512)
    var payerRef: String? = null,

    @Column(name = "order_id", length = 128)
    var orderId: String? = null,

    @Column(name = "amount_minor_units")
    var amountMinorUnits: Long? = null,

    @Column(name = "currency", length = 3)
    var currency: String? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
)

interface PaymentStatusRepository : JpaRepository<PaymentStatusEntity, UUID> {
    fun findTop5ByPayerRefOrderByUpdatedAtDesc(payerRef: String): List<PaymentStatusEntity>
}
