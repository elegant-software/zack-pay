package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "merchant_branch_appearances",
    schema = "blitzpay",
    indexes = [
        Index(name = "idx_appearances_branch_id", columnList = "branch_id"),
        Index(name = "idx_appearances_time_window", columnList = "start_time,end_time"),
    ]
)
class MerchantBranchAppearance(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    val branch: MerchantBranch? = null,

    @Column(name = "declared_latitude", nullable = false)
    val declaredLatitude: Double,

    @Column(name = "declared_longitude", nullable = false)
    val declaredLongitude: Double,

    @Column(name = "live_latitude")
    var liveLatitude: Double? = null,

    @Column(name = "live_longitude")
    var liveLongitude: Double? = null,

    @Column(name = "start_time", nullable = false)
    val startTime: Instant,

    @Column(name = "end_time", nullable = false)
    val endTime: Instant,

    @Column(name = "name")
    var name: String? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    val effectiveLatitude: Double get() = liveLatitude ?: declaredLatitude
    val effectiveLongitude: Double get() = liveLongitude ?: declaredLongitude

    fun isActiveAt(instant: Instant): Boolean = !instant.isBefore(startTime) && !instant.isAfter(endTime)

    fun cancel(at: Instant = Instant.now()) {
        require(cancelledAt == null) { "Appearance is already cancelled" }
        cancelledAt = at
        updatedAt = at
    }
}
