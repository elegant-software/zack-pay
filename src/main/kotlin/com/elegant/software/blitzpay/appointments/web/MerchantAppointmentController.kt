package com.elegant.software.blitzpay.appointments.web

import com.elegant.software.blitzpay.appointments.service.AppointmentService
import com.elegant.software.blitzpay.appointments.service.BookAppointmentCommand
import com.elegant.software.blitzpay.appointments.service.CancelledBy
import com.elegant.software.blitzpay.appointments.service.CompletedBy
import com.elegant.software.blitzpay.appointments.service.RescheduleCommand
import com.elegant.software.blitzpay.merchant.scheduling.AppointmentSchedulingService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/** Merchant-side calendar and appointment management endpoints. */
@RestController
@Tag(name = "Appointments", description = "Merchant-side calendar and appointment management")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/appointments", version = "1")
class MerchantAppointmentController(
    private val appointmentService: AppointmentService,
    private val schedulingService: AppointmentSchedulingService,
) {

    /** Merchant Manager: view all appointments for the merchant in a time window. */
    @GetMapping
    fun listByMerchant(
        @PathVariable merchantId: UUID,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
    ): ResponseEntity<List<AppointmentResponse>> {
        val appointments = appointmentService.findByMerchant(merchantId, from, to)
        return ResponseEntity.ok(appointments.map { it.toResponse() })
    }

    /** Merchant Employee / Manager: view appointments for a specific employee. */
    @GetMapping("/employee/{employeeId}")
    fun listByEmployee(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
    ): ResponseEntity<List<AppointmentResponse>> {
        val appointments = appointmentService.findByEmployee(employeeId, from, to)
        return ResponseEntity.ok(appointments.map { it.toResponse() })
    }

    /** Merchant Manager / Employee: create a contact-only appointment (no shopper account). */
    @PostMapping
    fun create(
        @PathVariable merchantId: UUID,
        @Valid @RequestBody req: MerchantBookAppointmentRequest,
    ): ResponseEntity<AppointmentResponse> {
        val branchId = req.branchId!!
        val context = schedulingService.loadSchedulingContext(
            branchId, req.employeeId,
            req.startTime!!.atZone(java.time.ZoneId.of("UTC")).toLocalDate(),
            req.startTime.atZone(java.time.ZoneId.of("UTC")).toLocalDate(),
        )

        val appointment = appointmentService.book(
            BookAppointmentCommand(
                merchantId = merchantId,
                branchId = branchId,
                productId = req.productId!!,
                productCategoryId = req.productCategoryId!!,
                startTime = req.startTime,
                employeeId = req.employeeId,
                shopperAccountId = null,
                contactName = req.contactName!!,
                contactEmail = req.contactEmail,
                contactPhone = req.contactPhone,
                note = req.note,
                branchTimezone = context.branchTimezone.id,
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment.toResponse())
    }

    @DeleteMapping("/{appointmentId}")
    fun cancel(
        @PathVariable merchantId: UUID,
        @PathVariable appointmentId: UUID,
    ): ResponseEntity<AppointmentResponse> {
        val updated = appointmentService.cancel(appointmentId, CancelledBy.MerchantManager)
        return ResponseEntity.ok(updated.toResponse())
    }

    @PutMapping("/{appointmentId}/complete")
    fun complete(
        @PathVariable merchantId: UUID,
        @PathVariable appointmentId: UUID,
    ): ResponseEntity<AppointmentResponse> {
        val updated = appointmentService.complete(appointmentId, CompletedBy.MerchantManager)
        return ResponseEntity.ok(updated.toResponse())
    }

    @PutMapping("/{appointmentId}/reschedule")
    fun reschedule(
        @PathVariable merchantId: UUID,
        @PathVariable appointmentId: UUID,
        @Valid @RequestBody req: RescheduleRequest,
    ): ResponseEntity<AppointmentResponse> {
        val appointment = appointmentService.findById(appointmentId)
        val tz = schedulingService.loadSchedulingContext(
            appointment.branchId, null,
            req.newStartTime!!.atZone(java.time.ZoneId.of("UTC")).toLocalDate(),
            req.newStartTime.atZone(java.time.ZoneId.of("UTC")).toLocalDate(),
        ).branchTimezone.id

        val updated = appointmentService.reschedule(
            appointmentId,
            RescheduleCommand(
                branchId = appointment.branchId,
                newProductId = req.newProductId!!,
                newProductCategoryId = req.newProductCategoryId!!,
                newStartTime = req.newStartTime,
                newEmployeeId = req.newEmployeeId,
                branchTimezone = tz,
            )
        )
        return ResponseEntity.ok(updated.toResponse())
    }

    /** Admin: support override with required reason. */
    @PutMapping("/{appointmentId}/override")
    fun supportOverride(
        @PathVariable merchantId: UUID,
        @PathVariable appointmentId: UUID,
        @Valid @RequestBody req: SupportOverrideRequest,
    ): ResponseEntity<AppointmentResponse> {
        val appointment = appointmentService.findById(appointmentId)
        val tz = schedulingService.loadSchedulingContext(
            appointment.branchId, null,
            req.newStartTime!!.atZone(java.time.ZoneId.of("UTC")).toLocalDate(),
            req.newStartTime.atZone(java.time.ZoneId.of("UTC")).toLocalDate(),
        ).branchTimezone.id

        val updated = appointmentService.reschedule(
            appointmentId,
            RescheduleCommand(
                branchId = appointment.branchId,
                newProductId = req.newProductId!!,
                newProductCategoryId = req.newProductCategoryId!!,
                newStartTime = req.newStartTime,
                newEmployeeId = req.newEmployeeId,
                branchTimezone = tz,
                overrideReason = req.reason,
            )
        )
        return ResponseEntity.ok(updated.toResponse())
    }
}

data class MerchantBookAppointmentRequest(
    @field:NotNull val branchId: UUID?,
    @field:NotNull val productId: UUID?,
    @field:NotNull val productCategoryId: UUID?,
    @field:NotNull val startTime: Instant?,
    val employeeId: UUID? = null,
    @field:NotBlank val contactName: String?,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val note: String? = null,
)

data class SupportOverrideRequest(
    @field:NotNull val newProductId: UUID?,
    @field:NotNull val newProductCategoryId: UUID?,
    @field:NotNull val newStartTime: Instant?,
    val newEmployeeId: UUID? = null,
    @field:NotBlank val reason: String?,
)
