package com.elegant.software.blitzpay.payments.push.persistence.model

import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class DeliveryOutcome { ACCEPTED, REJECTED, RETRIED, GIVEN_UP, PENDING }

@Entity
@Table(
    name = "push_delivery_attempt",
    indexes = [
        Index(name = "ix_push_delivery_payment_request", columnList = "payment_request_id"),
        Index(name = "ix_push_delivery_token", columnList = "expo_push_token"),
        Index(name = "ix_push_delivery_ticket", columnList = "ticket_id"),
    ],
)
class PushDeliveryAttemptEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,

    @Column(name = "payment_request_id", nullable = false)
    var paymentRequestId: UUID,

    @Column(name = "expo_push_token", nullable = false, length = 256)
    var expoPushToken: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false, length = 32)
    var statusCode: PaymentStatusCode,

    @Column(name = "ticket_id", length = 128)
    var ticketId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    var outcome: DeliveryOutcome,

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_outcome", length = 16)
    var receiptOutcome: DeliveryOutcome? = null,

    @Column(name = "error_code", length = 64)
    var errorCode: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

interface PushDeliveryAttemptRepository : JpaRepository<PushDeliveryAttemptEntity, UUID> {
    fun findByTicketId(ticketId: String): PushDeliveryAttemptEntity?
}
