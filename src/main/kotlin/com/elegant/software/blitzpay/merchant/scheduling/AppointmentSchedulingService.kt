package com.elegant.software.blitzpay.merchant.scheduling

import java.time.LocalDate
import java.util.UUID

interface AppointmentSchedulingService {

    /**
     * Returns everything needed to compute Bookable Time for a branch + date range.
     * Pass [employeeId] to restrict to one employee; null loads all active employees
     * assigned to the branch who can perform at least one category with a duration.
     */
    fun loadSchedulingContext(
        branchId: UUID,
        employeeId: UUID?,
        from: LocalDate,
        to: LocalDate,
    ): SchedulingContext

    /** Throws [IllegalStateException] when the merchant does not have Appointment Booking enabled or is inactive. */
    fun assertBookingEnabled(merchantId: UUID)

    fun isAppointmentBookingEnabled(merchantId: UUID): Boolean
}
