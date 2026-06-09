package com.elegant.software.blitzpay.appointments.api

import java.util.UUID

data class AppointmentCompleted(
    val appointmentId: UUID,
    val merchantId: UUID,
    val branchId: UUID,
    val productId: UUID,
    val shopperAccountId: String?,
)
