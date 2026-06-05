package com.elegant.software.blitzpay.appointments.service

import com.elegant.software.blitzpay.appointments.persistence.AppointmentEntity
import com.elegant.software.blitzpay.merchant.scheduling.DateWindowType
import com.elegant.software.blitzpay.merchant.scheduling.EmployeeSchedule
import com.elegant.software.blitzpay.merchant.scheduling.SchedulingContext
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

                    for ((windowStart, windowEnd) in intersection) {
                        var cursor = windowStart
                        while (!cursor.plusMinutes(duration.toLong()).isAfter(windowEnd)) {
                            val slotEnd = cursor.plusMinutes(duration.toLong())
                            val startInstant = toInstant(date, cursor, context)
                            val endInstant = toInstant(date, slotEnd, context)

                            if (startInstant.isAfter(leadCutoff) &&
                                !startInstant.isAfter(horizonCutoff) &&
                                !hasOverlap(booked, startInstant, endInstant)
                            ) {
                                slots += BookableSlot(emp.employeeId, emp.employeeName, startInstant, endInstant)
                            }

                            cursor = cursor.plusMinutes(context.bookingIntervalMinutes.toLong())
                        }
                    }
                }
            }

            date = date.plusDays(1)
        }

        return slots.sortedWith(compareBy({ it.startTime }, { it.employeeName }))
    }

    private fun effectiveWindows(
        date: LocalDate,
        recurringWindows: List<com.elegant.software.blitzpay.merchant.scheduling.WeeklyWindow>,
        exceptions: List<com.elegant.software.blitzpay.merchant.scheduling.DateWindow>,
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
