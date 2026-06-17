package com.elegant.software.blitzpay.payments.push.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@Entity
@Table(name = "push_processed_webhook_event")
class ProcessedWebhookEventEntity(
    @Id
    @Column(name = "event_id", nullable = false, length = 128)
    var eventId: String,

    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now(),
)

interface ProcessedWebhookEventRepository : JpaRepository<ProcessedWebhookEventEntity, String>
