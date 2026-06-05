package com.elegant.software.blitzpay.appointments.api

import java.time.Instant
import java.util.UUID

data class AppointmentReminderDue(
    val appointmentId: UUID,
    val shopperAccountId: String,
    val startTime: Instant,
    val productName: String,
)
