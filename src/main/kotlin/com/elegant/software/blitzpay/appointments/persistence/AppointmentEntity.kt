package com.elegant.software.blitzpay.appointments.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "appointments", schema = "blitzpay")
class AppointmentEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_id", nullable = false, updatable = false)
    val merchantId: UUID,

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,

    @Column(name = "product_id", nullable = false)
    var productId: UUID,

    @Column(name = "shopper_account_id", length = 256)
    val shopperAccountId: String? = null,

    @Column(name = "start_time", nullable = false)
    var startTime: Instant,

    @Column(name = "end_time", nullable = false)
    var endTime: Instant,

    @Column(nullable = false, length = 32)
    var status: String = AppointmentStatus.BOOKED,

    @Column(name = "contact_type", nullable = false, length = 32)
    var contactType: String,

    @Column(name = "contact_name", nullable = false, length = 255)
    var contactName: String,

    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null,

    @Column(name = "contact_phone", length = 64)
    var contactPhone: String? = null,

    @Column(name = "employee_snapshot_name", length = 255)
    var employeeSnapshotName: String? = null,

    @Column(name = "product_category_id")
    var productCategoryId: UUID? = null,

    @Column(name = "product_snapshot_name", nullable = false, length = 255)
    var productSnapshotName: String,

    @Column(name = "product_snapshot_price", precision = 12, scale = 4)
    var productSnapshotPrice: BigDecimal? = null,

    @Column(name = "duration_snapshot_minutes", nullable = false)
    var durationSnapshotMinutes: Int,

    @Column(name = "note", columnDefinition = "TEXT")
    var note: String? = null,

    @Column(name = "override_reason", columnDefinition = "TEXT")
    var overrideReason: String? = null,

    @Column(name = "reminder_due_at")
    var reminderDueAt: Instant? = null,

    @Column(name = "reminder_sent", nullable = false)
    var reminderSent: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
)

object AppointmentStatus {
    const val BOOKED = "BOOKED"
    const val CANCELLED = "CANCELLED"
    const val COMPLETED = "COMPLETED"
}

object AppointmentContactType {
    const val SHOPPER_OWNED = "SHOPPER_OWNED"
    const val MERCHANT_CAPTURED = "MERCHANT_CAPTURED"
}
