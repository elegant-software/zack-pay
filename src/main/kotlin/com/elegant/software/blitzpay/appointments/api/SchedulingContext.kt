package com.elegant.software.blitzpay.appointments.api

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class SchedulingContext(
    val merchantId: UUID,
    val branchId: UUID,
    val branchTimezone: ZoneId,
    val bookingIntervalMinutes: Int,
    val bookingLeadTimeMinutes: Int,
    val bookingHorizonDays: Int,
    val reminderLeadTimeMinutes: Int,
    val employees: List<EmployeeSchedule>,
    val branchOpeningWindows: List<WeeklyWindow>,
    val branchOpeningExceptions: List<DateWindow>,
    val bookableProducts: List<BookableProductInfo>,
    val productCategories: List<ProductCategoryInfo>,
)

data class EmployeeSchedule(
    val employeeId: UUID,
    val employeeName: String,
    val availabilityWindows: List<WeeklyWindow>,
    val availabilityExceptions: List<DateWindow>,
    val serviceProductCategoryIds: Set<UUID>,
)

data class WeeklyWindow(
    val dayOfWeek: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

data class DateWindow(
    val date: LocalDate,
    val type: DateWindowType,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
)

enum class DateWindowType { CLOSED, EXTRA_WINDOW }

data class BookableProductInfo(
    val productId: UUID,
    val productName: String,
    val productPrice: BigDecimal,
    val categoryId: UUID,
)

data class ProductCategoryInfo(
    val categoryId: UUID,
    val categoryName: String,
    val estimatedDurationMinutes: Int?,
)
