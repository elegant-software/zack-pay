package com.elegant.software.blitzpay.appointments.api

import java.time.Instant
import java.util.UUID

data class AppointmentBooked(
    val appointmentId: UUID,
    val merchantId: UUID,
    val branchId: UUID,
    val employeeId: UUID,
    val shopperAccountId: String?,
    val startTime: Instant,
    val productName: String,
    val employeeName: String,
)
