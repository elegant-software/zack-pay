package com.elegant.software.blitzpay.appointments.api

import java.time.LocalDate
import java.util.UUID

interface AppointmentSchedulingService {

    fun loadSchedulingContext(
        branchId: UUID,
        employeeId: UUID?,
        from: LocalDate,
        to: LocalDate,
    ): SchedulingContext

    fun assertBookingEnabled(merchantId: UUID)

    fun isAppointmentBookingEnabled(merchantId: UUID): Boolean
}
