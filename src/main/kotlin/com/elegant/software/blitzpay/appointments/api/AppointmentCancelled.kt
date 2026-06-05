package com.elegant.software.blitzpay.appointments.api

import java.util.UUID

data class AppointmentCancelled(
    val appointmentId: UUID,
    val merchantId: UUID,
    val shopperAccountId: String?,
    val productName: String,
    val employeeName: String,
)
