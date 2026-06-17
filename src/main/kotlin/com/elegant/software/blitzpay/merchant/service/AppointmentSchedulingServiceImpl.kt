package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranchOpeningException
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantEmployeeAvailabilityException
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantEntityStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchOpeningExceptionRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchOpeningHoursRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeAvailabilityExceptionRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeAvailabilityRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeBranchAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeServiceAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.appointments.api.AppointmentSchedulingService
import com.elegant.software.blitzpay.appointments.api.BookableProductInfo
import com.elegant.software.blitzpay.appointments.api.DateWindow
import com.elegant.software.blitzpay.appointments.api.DateWindowType
import com.elegant.software.blitzpay.appointments.api.EmployeeSchedule
import com.elegant.software.blitzpay.appointments.api.ProductCategoryInfo
import com.elegant.software.blitzpay.appointments.api.SchedulingContext
import com.elegant.software.blitzpay.appointments.api.WeeklyWindow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AppointmentSchedulingServiceImpl(
    private val merchantRepo: MerchantApplicationRepository,
    private val branchRepo: MerchantBranchRepository,
    private val employeeRepo: MerchantEmployeeRepository,
    private val branchAssignmentRepo: MerchantEmployeeBranchAssignmentRepository,
    private val availabilityRepo: MerchantEmployeeAvailabilityRepository,
    private val availabilityExceptionRepo: MerchantEmployeeAvailabilityExceptionRepository,
    private val serviceAssignmentRepo: MerchantEmployeeServiceAssignmentRepository,
    private val openingHoursRepo: MerchantBranchOpeningHoursRepository,
    private val openingExceptionRepo: MerchantBranchOpeningExceptionRepository,
    private val productRepo: MerchantProductRepository,
    private val categoryRepo: MerchantProductCategoryRepository,
    private val offeringAssignmentRepo: MerchantOfferingAssignmentRepository,
) : AppointmentSchedulingService {

    private val log = LoggerFactory.getLogger(AppointmentSchedulingServiceImpl::class.java)

    override fun assertBookingEnabled(merchantId: UUID) {
        val merchant = merchantRepo.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant $merchantId not found") }
        check(merchant.merchantStatus.name == "ACTIVE") { "Merchant is not active" }
        check(isAppointmentBookingEnabled(merchantId)) { "Appointment Booking is not enabled for this merchant" }
    }

    override fun isAppointmentBookingEnabled(merchantId: UUID): Boolean =
        offeringAssignmentRepo.existsByMerchantApplicationIdAndOfferingCode(merchantId, "APPOINTMENT_BOOKING")

    override fun loadSchedulingContext(
        branchId: UUID,
        employeeId: UUID?,
        from: LocalDate,
        to: LocalDate,
    ): SchedulingContext {
        val branch = branchRepo.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch $branchId not found") }
        val merchant = merchantRepo.findById(branch.merchantApplicationId)
            .orElseThrow { NoSuchElementException("Merchant not found for branch $branchId") }

        check(merchant.merchantStatus == com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus.ACTIVE) {
            "Merchant is not active"
        }
        check(branch.status == com.elegant.software.blitzpay.merchant.persistence.model.MerchantEntityStatus.ACTIVE) {
            "Branch is not active"
        }

        val timezone = branch.timezone?.let { ZoneId.of(it) } ?: ZoneId.of("UTC")

        val employeeIds: List<UUID> = if (employeeId != null) {
            listOf(employeeId)
        } else {
            branchAssignmentRepo.findAllByBranchId(branchId).map { it.employeeId }
        }

        val activeEmployees = employeeRepo.findAllById(employeeIds)
            .filter { it.status == MerchantEntityStatus.ACTIVE }

        val allServiceAssignments = serviceAssignmentRepo.findAllByEmployeeIdIn(activeEmployees.map { it.id })
            .groupBy { it.employeeId }

        val allAvailability = availabilityRepo.findAllByEmployeeIdIn(activeEmployees.map { it.id })
            .groupBy { it.employeeId }

        val allExceptions = availabilityExceptionRepo
            .findAllByEmployeeIdInAndExceptionDateBetween(activeEmployees.map { it.id }, from, to)
            .groupBy { it.employeeId }

        val employees = activeEmployees.map { emp ->
            EmployeeSchedule(
                employeeId = emp.id,
                employeeName = emp.name,
                availabilityWindows = (allAvailability[emp.id] ?: emptyList()).map { avail ->
                    WeeklyWindow(avail.dayOfWeek, avail.startTime, avail.endTime)
                },
                availabilityExceptions = (allExceptions[emp.id] ?: emptyList()).map { exc ->
                    DateWindow(exc.exceptionDate, exc.type.toDateWindowType(), exc.startTime, exc.endTime)
                },
                serviceProductCategoryIds = (allServiceAssignments[emp.id] ?: emptyList())
                    .map { it.productCategoryId }.toSet(),
            )
        }

        val openingHours = openingHoursRepo.findAllByBranchId(branchId).map { h ->
            WeeklyWindow(h.dayOfWeek, h.startTime, h.endTime)
        }
        val openingExceptions = openingExceptionRepo
            .findAllByBranchIdAndExceptionDateBetween(branchId, from, to).map { exc ->
                DateWindow(exc.exceptionDate, exc.type.toDateWindowType(), exc.startTime, exc.endTime)
            }

        val products = productRepo.findAllByActiveTrueAndMerchantBranchId(branchId).map { p ->
            BookableProductInfo(p.id, p.name, p.unitPrice, p.productCategoryId ?: error("product ${p.id} has no category"))
        }
        val categoryIds = products.map { it.categoryId }.toSet()
        val categories = categoryRepo.findAllById(categoryIds).map { c ->
            ProductCategoryInfo(c.id, c.name, c.estimatedDurationMinutes)
        }

        return SchedulingContext(
            merchantId = merchant.id,
            branchId = branchId,
            branchTimezone = timezone,
            bookingIntervalMinutes = merchant.bookingIntervalMinutes ?: 30,
            bookingLeadTimeMinutes = merchant.bookingLeadTimeMinutes ?: 60,
            bookingHorizonDays = merchant.bookingHorizonDays ?: 30,
            reminderLeadTimeMinutes = merchant.reminderLeadTimeMinutes ?: 1440,
            employees = employees,
            branchOpeningWindows = openingHours,
            branchOpeningExceptions = openingExceptions,
            bookableProducts = products,
            productCategories = categories,
        )
    }

    private fun String.toDateWindowType(): DateWindowType = when (this) {
        MerchantBranchOpeningException.CLOSED, MerchantEmployeeAvailabilityException.CLOSED -> DateWindowType.CLOSED
        else -> DateWindowType.EXTRA_WINDOW
    }
}
