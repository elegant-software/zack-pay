package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_monitoring_records")
class MonitoringRecord(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MonitoringStatus = MonitoringStatus.ACTIVE,

    @Column(nullable = false)
    var lastTriggerReason: String,

    @Column(nullable = false)
    var lastReviewedAt: Instant = Instant.now(),

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    fun recordFollowUp(triggerReason: String, reviewedAt: Instant = Instant.now()) {
        status = MonitoringStatus.REVIEW_REQUIRED
        lastTriggerReason = triggerReason
        lastReviewedAt = reviewedAt
    }

    fun markCleared(reviewedAt: Instant = Instant.now()) {
        status = MonitoringStatus.ACTIVE
        lastReviewedAt = reviewedAt
    }
}

enum class MonitoringStatus {
    ACTIVE,
    REVIEW_REQUIRED,
    SUSPENDED
}
