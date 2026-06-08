package com.elegant.software.blitzpay.appointments.service

import com.elegant.software.blitzpay.appointments.api.AppointmentBooked
import com.elegant.software.blitzpay.appointments.api.AppointmentCancelled
import com.elegant.software.blitzpay.appointments.api.AppointmentRescheduled
import com.elegant.software.blitzpay.appointments.persistence.AppointmentContactType
import com.elegant.software.blitzpay.appointments.persistence.AppointmentEntity
import com.elegant.software.blitzpay.appointments.persistence.AppointmentRepository
import com.elegant.software.blitzpay.appointments.persistence.AppointmentStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeServiceAssignmentRepository
import com.elegant.software.blitzpay.merchant.scheduling.AppointmentSchedulingService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
class AppointmentService(
    private val appointmentRepo: AppointmentRepository,
    private val employeeRepo: MerchantEmployeeRepository,
    private val serviceAssignmentRepo: MerchantEmployeeServiceAssignmentRepository,
    private val schedulingService: AppointmentSchedulingService,
    private val bookableTimeService: BookableTimeService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(AppointmentService::class.java)

    @Transactional
    fun book(cmd: BookAppointmentCommand): AppointmentEntity {
        schedulingService.assertBookingEnabled(cmd.merchantId)

        val tz = ZoneId.of(cmd.branchTimezone ?: "UTC")
        val from = cmd.startTime.atZone(tz).toLocalDate()
        val context = schedulingService.loadSchedulingContext(cmd.branchId, cmd.employeeId, from, from)

        val resolvedEmployeeId = resolveEmployee(cmd, context, tz)

        val slot = bookableTimeService.compute(
            context = context,
            productCategoryId = cmd.productCategoryId,
            requestedEmployeeId = resolvedEmployeeId,
            from = from,
            to = from,
            existingAppointments = appointmentRepo.findAllByEmployeeIdAndStatusAndStartTimeBetween(
                resolvedEmployeeId, AppointmentStatus.BOOKED,
                from.atStartOfDay(tz).toInstant(),
                from.plusDays(1).atStartOfDay(tz).toInstant(),
            ),
            now = Instant.now(),
        ).firstOrNull { it.startTime == cmd.startTime && it.employeeId == resolvedEmployeeId }
            ?: throw IllegalStateException("Requested time is not available")

        // Acquire employee-level lock before inserting
        employeeRepo.findByIdForUpdate(resolvedEmployeeId)
            ?: throw NoSuchElementException("Employee $resolvedEmployeeId not found")

        // Re-check overlap under lock
        val overlap = appointmentRepo.findOverlappingBooked(resolvedEmployeeId, slot.startTime, slot.endTime)
        check(overlap.isEmpty()) { "Time slot is no longer available" }

        // Check shopper overlap
        cmd.shopperAccountId?.let { sid ->
            val shopperOverlap = appointmentRepo.findShopperOverlappingBooked(sid, slot.startTime, slot.endTime)
            check(shopperOverlap.isEmpty()) { "You already have an overlapping appointment" }
        }

        val product = context.bookableProducts.first { it.productId == cmd.productId }
        val category = context.productCategories.first { it.categoryId == cmd.productCategoryId }
        val employee = context.employees.first { it.employeeId == resolvedEmployeeId }

        val reminderDueAt = cmd.shopperAccountId?.let {
            slot.startTime.minusSeconds(context.reminderLeadTimeMinutes.toLong() * 60)
                .takeIf { t -> t.isAfter(Instant.now()) }
                ?: Instant.now()
        }

        val appointment = AppointmentEntity(
            merchantId = cmd.merchantId,
            branchId = cmd.branchId,
            employeeId = resolvedEmployeeId,
            productId = cmd.productId,
            shopperAccountId = cmd.shopperAccountId,
            startTime = slot.startTime,
            endTime = slot.endTime,
            status = AppointmentStatus.BOOKED,
            contactType = if (cmd.shopperAccountId != null) AppointmentContactType.SHOPPER_OWNED else AppointmentContactType.MERCHANT_CAPTURED,
            contactName = cmd.contactName,
            contactEmail = cmd.contactEmail,
            contactPhone = cmd.contactPhone,
            productSnapshotName = product.productName,
            productSnapshotPrice = product.productPrice,
            durationSnapshotMinutes = category.estimatedDurationMinutes!!,
            note = cmd.note,
            reminderDueAt = reminderDueAt,
        )

        appointmentRepo.save(appointment)
        log.info("Appointment {} booked: employee={} start={}", appointment.id, resolvedEmployeeId, slot.startTime)

        eventPublisher.publishEvent(
            AppointmentBooked(
                appointmentId = appointment.id,
                merchantId = cmd.merchantId,
                branchId = cmd.branchId,
                employeeId = resolvedEmployeeId,
                shopperAccountId = cmd.shopperAccountId,
                startTime = slot.startTime,
                productName = product.productName,
                employeeName = employee.employeeName,
            )
        )

        return appointment
    }

    @Transactional
    fun cancel(appointmentId: UUID, cancelledBy: CancelledBy): AppointmentEntity {
        val appointment = appointmentRepo.findByIdForUpdate(appointmentId)
            ?: throw NoSuchElementException("Appointment $appointmentId not found")

        check(appointment.status == AppointmentStatus.BOOKED) { "Only BOOKED appointments can be cancelled" }

        when (cancelledBy) {
            is CancelledBy.Shopper -> {
                check(appointment.shopperAccountId == cancelledBy.shopperAccountId) { "Not your appointment" }
                check(appointment.startTime.isAfter(Instant.now())) { "Cannot cancel a past appointment" }
            }
            is CancelledBy.MerchantEmployee -> {
                check(appointment.employeeId == cancelledBy.employeeId) { "Not assigned to your calendar" }
            }
            is CancelledBy.MerchantManager, is CancelledBy.Support -> Unit
        }

        appointment.status = AppointmentStatus.CANCELLED
        appointment.updatedAt = Instant.now()
        if (cancelledBy is CancelledBy.Support) {
            appointment.overrideReason = cancelledBy.reason
        }

        log.info("Appointment {} cancelled by {}", appointmentId, cancelledBy::class.simpleName)

        eventPublisher.publishEvent(
            AppointmentCancelled(
                appointmentId = appointment.id,
                merchantId = appointment.merchantId,
                shopperAccountId = appointment.shopperAccountId,
                productName = appointment.productSnapshotName,
                employeeName = resolveEmployeeName(appointment.employeeId),
            )
        )

        return appointment
    }

    @Transactional
    fun complete(appointmentId: UUID, completedBy: CompletedBy): AppointmentEntity {
        val appointment = appointmentRepo.findByIdForUpdate(appointmentId)
            ?: throw NoSuchElementException("Appointment $appointmentId not found")

        check(appointment.status == AppointmentStatus.BOOKED) { "Only BOOKED appointments can be completed" }
        check(!appointment.startTime.isAfter(Instant.now())) { "Cannot complete a future appointment" }

        when (completedBy) {
            is CompletedBy.MerchantEmployee ->
                check(appointment.employeeId == completedBy.employeeId) { "Not assigned to your calendar" }
            is CompletedBy.MerchantManager -> Unit
        }

        appointment.status = AppointmentStatus.COMPLETED
        appointment.updatedAt = Instant.now()
        return appointment
    }

    @Transactional
    fun reschedule(appointmentId: UUID, cmd: RescheduleCommand): AppointmentEntity {
        val appointment = appointmentRepo.findByIdForUpdate(appointmentId)
            ?: throw NoSuchElementException("Appointment $appointmentId not found")

        check(appointment.status == AppointmentStatus.BOOKED) { "Only BOOKED appointments can be rescheduled" }
        check(appointment.startTime.isAfter(Instant.now())) { "Cannot reschedule a past appointment" }

        val tz = ZoneId.of(cmd.branchTimezone ?: "UTC")
        val from = cmd.newStartTime.atZone(tz).toLocalDate()
        val context = schedulingService.loadSchedulingContext(cmd.branchId, cmd.newEmployeeId, from, from)

        val targetEmployeeId = cmd.newEmployeeId ?: appointment.employeeId

        // Validate employee can perform the new product category
        val newCategory = context.productCategories.first { it.categoryId == cmd.newProductCategoryId }
        val empSchedule = context.employees.first { it.employeeId == targetEmployeeId }
        check(cmd.newProductCategoryId in empSchedule.serviceProductCategoryIds) {
            "Employee cannot perform the selected product category"
        }

        val slot = bookableTimeService.compute(
            context = context,
            productCategoryId = cmd.newProductCategoryId,
            requestedEmployeeId = targetEmployeeId,
            from = from,
            to = from,
            existingAppointments = appointmentRepo.findAllByEmployeeIdAndStatusAndStartTimeBetween(
                targetEmployeeId, AppointmentStatus.BOOKED,
                from.atStartOfDay(tz).toInstant(),
                from.plusDays(1).atStartOfDay(tz).toInstant(),
            ).filter { it.id != appointmentId },
            now = Instant.now(),
        ).firstOrNull { it.startTime == cmd.newStartTime }
            ?: throw IllegalStateException("Requested time is not available")

        employeeRepo.findByIdForUpdate(targetEmployeeId)

        val overlap = appointmentRepo.findOverlappingBooked(targetEmployeeId, slot.startTime, slot.endTime)
            .filter { it.id != appointmentId }
        check(overlap.isEmpty()) { "Time slot is no longer available" }

        appointment.shopperAccountId?.let { sid ->
            val shopperOverlap = appointmentRepo.findShopperOverlappingBooked(sid, slot.startTime, slot.endTime)
                .filter { it.id != appointmentId }
            check(shopperOverlap.isEmpty()) { "You already have an overlapping appointment" }
        }

        val product = context.bookableProducts.first { it.productId == cmd.newProductId }
        val now = Instant.now()

        appointment.employeeId = targetEmployeeId
        appointment.productId = cmd.newProductId
        appointment.startTime = slot.startTime
        appointment.endTime = slot.endTime
        appointment.productSnapshotName = product.productName
        appointment.productSnapshotPrice = product.productPrice
        appointment.durationSnapshotMinutes = newCategory.estimatedDurationMinutes!!
        appointment.reminderDueAt = appointment.shopperAccountId?.let {
            slot.startTime.minusSeconds(context.reminderLeadTimeMinutes.toLong() * 60)
                .takeIf { t -> t.isAfter(now) } ?: now
        }
        appointment.reminderSent = false
        appointment.updatedAt = now
        if (cmd.overrideReason != null) appointment.overrideReason = cmd.overrideReason

        log.info("Appointment {} rescheduled to {} employee={}", appointmentId, slot.startTime, targetEmployeeId)

        eventPublisher.publishEvent(
            AppointmentRescheduled(
                appointmentId = appointment.id,
                merchantId = appointment.merchantId,
                shopperAccountId = appointment.shopperAccountId,
                newStartTime = slot.startTime,
                productName = product.productName,
                employeeName = empSchedule.employeeName,
            )
        )

        return appointment
    }

    @Transactional
    fun updateDetails(appointmentId: UUID, cmd: UpdateDetailsCommand): AppointmentEntity {
        val appointment = appointmentRepo.findByIdForUpdate(appointmentId)
            ?: throw NoSuchElementException("Appointment $appointmentId not found")
        check(appointment.status == AppointmentStatus.BOOKED) { "Only BOOKED appointments can be edited" }

        cmd.note?.let { appointment.note = it.value }

        val hasContactUpdate = cmd.contactName != null || cmd.contactEmail != null || cmd.contactPhone != null
        if (hasContactUpdate) {
            check(appointment.contactType == AppointmentContactType.MERCHANT_CAPTURED) {
                "Contact details on shopper-owned appointments may only be edited by the shopper"
            }
            cmd.contactName?.let { appointment.contactName = it }
            cmd.contactEmail?.let { appointment.contactEmail = it.value }
            cmd.contactPhone?.let { appointment.contactPhone = it.value }
        }

        appointment.updatedAt = Instant.now()
        return appointment
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): AppointmentEntity =
        appointmentRepo.findById(id).orElseThrow { NoSuchElementException("Appointment $id not found") }

    @Transactional(readOnly = true)
    fun findByShopperAccountId(shopperAccountId: String): List<AppointmentEntity> =
        appointmentRepo.findAllByShopperAccountId(shopperAccountId)

    @Transactional(readOnly = true)
    fun findByMerchant(merchantId: UUID, from: Instant, to: Instant): List<AppointmentEntity> =
        appointmentRepo.findAllByMerchantIdAndStartTimeBetween(merchantId, from, to)

    @Transactional(readOnly = true)
    fun findByBranch(branchId: UUID, from: Instant, to: Instant): List<AppointmentEntity> =
        appointmentRepo.findAllByBranchIdAndStartTimeBetween(branchId, from, to)

    @Transactional(readOnly = true)
    fun findByEmployee(employeeId: UUID, from: Instant, to: Instant): List<AppointmentEntity> =
        appointmentRepo.findAllByEmployeeIdAndStatusAndStartTimeBetween(employeeId, AppointmentStatus.BOOKED, from, to)

    private fun resolveEmployee(cmd: BookAppointmentCommand, context: com.elegant.software.blitzpay.merchant.scheduling.SchedulingContext, tz: ZoneId): UUID {
        if (cmd.employeeId != null) return cmd.employeeId

        val from = cmd.startTime.atZone(tz).toLocalDate()
        val dayStart = from.atStartOfDay(tz).toInstant()
        val dayEnd = from.plusDays(1).atStartOfDay(tz).toInstant()

        val qualified = context.employees.filter { cmd.productCategoryId in it.serviceProductCategoryIds }

        val countByEmployee = qualified.associate { emp ->
            emp.employeeId to appointmentRepo.findAllByEmployeeIdAndStatusAndStartTimeBetween(
                emp.employeeId, AppointmentStatus.BOOKED, dayStart, dayEnd,
            ).size
        }

        return qualified
            .sortedWith(compareBy({ countByEmployee[it.employeeId] ?: 0 }, { it.employeeName }, { it.employeeId.toString() }))
            .firstOrNull()?.employeeId
            ?: throw IllegalStateException("No qualified employee available")
    }

    private fun resolveEmployeeName(employeeId: UUID): String =
        employeeRepo.findById(employeeId).map { it.name }.orElse("Unknown")
}

data class BookAppointmentCommand(
    val merchantId: UUID,
    val branchId: UUID,
    val productId: UUID,
    val productCategoryId: UUID,
    val startTime: Instant,
    val employeeId: UUID?,
    val shopperAccountId: String?,
    val contactName: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val note: String?,
    val branchTimezone: String?,
)

data class RescheduleCommand(
    val branchId: UUID,
    val newProductId: UUID,
    val newProductCategoryId: UUID,
    val newStartTime: Instant,
    val newEmployeeId: UUID?,
    val branchTimezone: String?,
    val overrideReason: String? = null,
)

sealed class CancelledBy {
    data class Shopper(val shopperAccountId: String) : CancelledBy()
    data class MerchantEmployee(val employeeId: UUID) : CancelledBy()
    object MerchantManager : CancelledBy()
    data class Support(val reason: String) : CancelledBy()
}

/** Wraps a field that can be explicitly set to null (distinct from "not provided"). */
data class Nullable<out T>(val value: T?)

data class UpdateDetailsCommand(
    val note: Nullable<String>? = null,
    val contactName: String? = null,
    val contactEmail: Nullable<String>? = null,
    val contactPhone: Nullable<String>? = null,
)

sealed class CompletedBy {
    data class MerchantEmployee(val employeeId: UUID) : CompletedBy()
    object MerchantManager : CompletedBy()
}
