package com.elegant.software.blitzpay.appointments.service

import com.elegant.software.blitzpay.appointments.persistence.AppointmentEntity
import com.elegant.software.blitzpay.appointments.api.DateWindowType
import com.elegant.software.blitzpay.appointments.api.DateWindow
import com.elegant.software.blitzpay.appointments.api.SchedulingContext
import com.elegant.software.blitzpay.appointments.api.WeeklyWindow
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

data class BookableSlot(
    val employeeId: UUID,
    val employeeName: String,
    val startTime: Instant,
    val endTime: Instant,
)

@Component
class BookableTimeService {

    fun compute(
        context: SchedulingContext,
        productCategoryId: UUID,
        requestedEmployeeId: UUID?,
        from: LocalDate,
        to: LocalDate,
        existingAppointments: List<AppointmentEntity>,
        now: Instant,
    ): List<BookableSlot> {
        val duration = context.productCategories
            .firstOrNull { it.categoryId == productCategoryId }
            ?.estimatedDurationMinutes
            ?: return emptyList()

        val horizonCutoff = now.plusSeconds(context.bookingHorizonDays.toLong() * 86_400)
        val leadCutoff = now.plusSeconds(context.bookingLeadTimeMinutes.toLong() * 60)

        val bookedByEmployee = existingAppointments
            .filter { it.status == "BOOKED" }
            .groupBy { it.employeeId }

        val candidates = context.employees
            .filter { emp ->
                productCategoryId in emp.serviceProductCategoryIds &&
                    (requestedEmployeeId == null || emp.employeeId == requestedEmployeeId)
            }

        val slots = mutableListOf<BookableSlot>()

        var date = from
        while (!date.isAfter(to)) {
            val dayOfWeek = date.dayOfWeek.value  // 1=Monday, 7=Sunday

            val branchWindows = effectiveWindows(
                date = date,
                recurringWindows = context.branchOpeningWindows.filter { it.dayOfWeek == dayOfWeek },
                exceptions = context.branchOpeningExceptions.filter { it.date == date },
            )

            if (branchWindows.isNotEmpty()) {
                for (emp in candidates) {
                    val empWindows = effectiveWindows(
                        date = date,
                        recurringWindows = emp.availabilityWindows.filter { it.dayOfWeek == dayOfWeek },
                        exceptions = emp.availabilityExceptions.filter { it.date == date },
                    )

                    val intersection = intersect(branchWindows, empWindows)
                    val booked = bookedByEmployee[emp.employeeId] ?: emptyList()

                    val interval = context.bookingIntervalMinutes
                    for ((windowStart, windowEnd) in intersection) {
                        if (interval <= 0) continue
                        var cursorMins = toMinutes(windowStart)
                        val windowEndMins = toMinutes(windowEnd)
                        while (cursorMins + duration <= windowEndMins) {
                            val cursor = LocalTime.of(cursorMins / 60, cursorMins % 60)
                            val slotEndMins = cursorMins + duration
                            val slotEnd = LocalTime.of(slotEndMins / 60, slotEndMins % 60)
                            val startInstant = toInstant(date, cursor, context)
                            val endInstant = toInstant(date, slotEnd, context)

                            if (startInstant.isAfter(leadCutoff) &&
                                !startInstant.isAfter(horizonCutoff) &&
                                !hasOverlap(booked, startInstant, endInstant)
                            ) {
                                slots += BookableSlot(emp.employeeId, emp.employeeName, startInstant, endInstant)
                            }

                            cursorMins += interval
                        }
                    }
                }
            }

            date = date.plusDays(1)
        }

        return slots.sortedWith(compareBy({ it.startTime }, { it.employeeName }))
    }

    fun diagnose(
        context: SchedulingContext,
        productCategoryId: UUID,
        requestedEmployeeId: UUID,
        requestedStartTime: Instant,
        existingAppointments: List<AppointmentEntity>,
        now: Instant,
    ): String {
        val duration = context.productCategories
            .firstOrNull { it.categoryId == productCategoryId }
            ?.estimatedDurationMinutes
            ?: return "Product category not found or has no duration configured."

        val leadCutoff = now.plusSeconds(context.bookingLeadTimeMinutes.toLong() * 60)
        val horizonCutoff = now.plusSeconds(context.bookingHorizonDays.toLong() * 86_400)

        if (!requestedStartTime.isAfter(leadCutoff))
            return "Too soon: appointments require at least ${context.bookingLeadTimeMinutes} minutes lead time."
        if (requestedStartTime.isAfter(horizonCutoff))
            return "Too far ahead: bookings can only be made up to ${context.bookingHorizonDays} days in advance."

        val tz = context.branchTimezone
        val zdt = requestedStartTime.atZone(tz)
        val requestedDate = zdt.toLocalDate()
        val requestedLocalTime = zdt.toLocalTime()
        val dayOfWeek = requestedDate.dayOfWeek.value
        val requestedStartMins = toMinutes(requestedLocalTime)
        val requestedEndMins = requestedStartMins + duration

        val emp = context.employees.firstOrNull { it.employeeId == requestedEmployeeId }
            ?: return "Employee is not assigned to this branch."
        if (productCategoryId !in emp.serviceProductCategoryIds)
            return "The selected employee cannot perform this service."

        val branchWindows = effectiveWindows(
            date = requestedDate,
            recurringWindows = context.branchOpeningWindows.filter { it.dayOfWeek == dayOfWeek },
            exceptions = context.branchOpeningExceptions.filter { it.date == requestedDate },
        )
        if (branchWindows.isEmpty())
            return "The branch is closed on ${requestedDate.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }}."
        val branchStartCovered = branchWindows.any { (ws, _) -> requestedStartMins >= toMinutes(ws) && requestedStartMins < toMinutes(ws) + 1440 }
        val branchFits = branchWindows.any { (ws, we) -> requestedStartMins >= toMinutes(ws) && requestedEndMins <= toMinutes(we) }
        if (!branchFits) {
            val periods = branchWindows.joinToString(", ") { (s, e) -> "$s–$e" }
            return if (!branchStartCovered) "The requested time is outside branch opening hours ($periods)."
            else "The ${duration}-minute service would end after branch closing time ($periods)."
        }

        val empWindows = effectiveWindows(
            date = requestedDate,
            recurringWindows = emp.availabilityWindows.filter { it.dayOfWeek == dayOfWeek },
            exceptions = emp.availabilityExceptions.filter { it.date == requestedDate },
        )
        if (empWindows.isEmpty())
            return "Employee '${emp.employeeName}' is not available on ${requestedDate.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }}."
        val empStartCovered = empWindows.any { (ws, we) -> requestedStartMins >= toMinutes(ws) && requestedStartMins < toMinutes(we) }
        val empFits = empWindows.any { (ws, we) -> requestedStartMins >= toMinutes(ws) && requestedEndMins <= toMinutes(we) }
        if (!empFits) {
            val periods = empWindows.joinToString(", ") { (s, e) -> "$s–$e" }
            return if (!empStartCovered) "Employee '${emp.employeeName}' is not available at $requestedLocalTime (available: $periods)."
            else {
                val endH = requestedEndMins / 60
                val endM = requestedEndMins % 60
                "The ${duration}-minute service would end at %02d:%02d but employee '${emp.employeeName}' is only available until $periods.".format(endH, endM)
            }
        }

        val intersection = intersect(branchWindows, empWindows)
        val alignedToInterval = intersection.any { (ws, _) ->
            val diff = requestedStartMins - toMinutes(ws)
            diff >= 0 && diff % context.bookingIntervalMinutes == 0
        }
        if (!alignedToInterval)
            return "The requested time is not on a valid booking slot (slots every ${context.bookingIntervalMinutes} minutes)."

        val requestedEndInstant = requestedStartTime.plusSeconds(duration.toLong() * 60)
        val bookedForEmp = existingAppointments.filter { it.employeeId == requestedEmployeeId }
        if (hasOverlap(bookedForEmp, requestedStartTime, requestedEndInstant))
            return "The time slot is already taken by another appointment."

        return "The requested time is not available."
    }

    private fun toMinutes(time: LocalTime): Int = time.hour * 60 + time.minute

    private fun effectiveWindows(
        date: LocalDate,
        recurringWindows: List<WeeklyWindow>,
        exceptions: List<DateWindow>,
    ): List<Pair<LocalTime, LocalTime>> {
        if (exceptions.isEmpty()) {
            return recurringWindows.map { it.startTime to it.endTime }
        }
        val closedException = exceptions.firstOrNull { it.type == DateWindowType.CLOSED }
        if (closedException != null) return emptyList()
        val base = recurringWindows.map { it.startTime to it.endTime }
        val extras = exceptions.filter { it.type == DateWindowType.EXTRA_WINDOW && it.startTime != null && it.endTime != null }
            .map { it.startTime!! to it.endTime!! }
        return base + extras
    }

    private fun intersect(
        a: List<Pair<LocalTime, LocalTime>>,
        b: List<Pair<LocalTime, LocalTime>>,
    ): List<Pair<LocalTime, LocalTime>> {
        val result = mutableListOf<Pair<LocalTime, LocalTime>>()
        for ((aStart, aEnd) in a) {
            for ((bStart, bEnd) in b) {
                val start = maxOf(aStart, bStart)
                val end = minOf(aEnd, bEnd)
                if (start.isBefore(end)) result += start to end
            }
        }
        return result
    }

    private fun toInstant(date: LocalDate, time: LocalTime, context: SchedulingContext): Instant =
        LocalDateTime.of(date, time).atZone(context.branchTimezone).toInstant()

    private fun hasOverlap(booked: List<AppointmentEntity>, start: Instant, end: Instant): Boolean =
        booked.any { it.startTime.isBefore(end) && it.endTime.isAfter(start) }
}
