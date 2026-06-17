package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "merchant_employee_availability_exceptions", schema = "blitzpay")
class MerchantEmployeeAvailabilityException(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "employee_id", nullable = false, updatable = false)
    val employeeId: UUID,

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @Column(name = "exception_date", nullable = false, updatable = false)
    val exceptionDate: LocalDate,

    /** CLOSED removes availability for this date; EXTRA_WINDOW adds extra availability */
    @Column(nullable = false, length = 32)
    val type: String,

    @Column(name = "start_time")
    val startTime: LocalTime? = null,

    @Column(name = "end_time")
    val endTime: LocalTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        const val CLOSED = "CLOSED"
        const val EXTRA_WINDOW = "EXTRA_WINDOW"
    }
}
