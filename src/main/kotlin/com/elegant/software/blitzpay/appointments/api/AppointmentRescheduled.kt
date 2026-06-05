package com.elegant.software.blitzpay.appointments.api

import java.time.Instant
import java.util.UUID

data class AppointmentRescheduled(
    val appointmentId: UUID,
    val merchantId: UUID,
    val shopperAccountId: String?,
    val newStartTime: Instant,
    val productName: String,
    val employeeName: String,
)
