package com.elegant.software.blitzpay.appointments.web

import com.elegant.software.blitzpay.appointments.persistence.AppointmentEntity
import com.elegant.software.blitzpay.appointments.service.BookableSlot
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BookableTimeRequest(
    @field:NotNull val branchId: UUID?,
    @field:NotNull val productCategoryId: UUID?,
    val employeeId: UUID? = null,
    @field:NotNull val from: Instant?,
    @field:NotNull val to: Instant?,
)

data class BookableSlotResponse(
    val employeeId: UUID,
    val employeeName: String,
    val startTime: Instant,
    val endTime: Instant,
)

fun BookableSlot.toResponse() = BookableSlotResponse(employeeId, employeeName, startTime, endTime)

data class BookAppointmentRequest(
    @field:NotNull val branchId: UUID?,
    @field:NotNull val productId: UUID?,
    @field:NotNull val productCategoryId: UUID?,
    @field:NotNull val startTime: Instant?,
    val employeeId: UUID? = null,
    @field:NotBlank val contactName: String?,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val note: String? = null,
)

data class RescheduleRequest(
    @field:NotNull val newProductId: UUID?,
    @field:NotNull val newProductCategoryId: UUID?,
    @field:NotNull val newStartTime: Instant?,
    val newEmployeeId: UUID? = null,
    val overrideReason: String? = null,
)

data class AppointmentResponse(
    val id: UUID,
    val merchantId: UUID,
    val branchId: UUID,
    val employeeId: UUID,
    val employeeName: String?,
    val productId: UUID,
    val productCategoryId: UUID?,
    val shopperAccountId: String?,
    val startTime: Instant,
    val endTime: Instant,
    val status: String,
    val contactType: String,
    val contactName: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val productName: String,
    val productPrice: BigDecimal?,
    val durationMinutes: Int,
    val note: String?,
    val cancelledReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun AppointmentEntity.toResponse() = AppointmentResponse(
    id = id,
    merchantId = merchantId,
    branchId = branchId,
    employeeId = employeeId,
    employeeName = employeeSnapshotName,
    productId = productId,
    productCategoryId = productCategoryId,
    shopperAccountId = shopperAccountId,
    startTime = startTime,
    endTime = endTime,
    status = status,
    contactType = contactType,
    contactName = contactName,
    contactEmail = contactEmail,
    contactPhone = contactPhone,
    productName = productSnapshotName,
    productPrice = productSnapshotPrice,
    durationMinutes = durationSnapshotMinutes,
    note = note,
    cancelledReason = overrideReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
