package com.elegant.software.blitzpay.appointments.web

import com.elegant.software.blitzpay.appointments.persistence.AppointmentRepository
import com.elegant.software.blitzpay.appointments.persistence.AppointmentStatus
import com.elegant.software.blitzpay.appointments.service.AppointmentService
import com.elegant.software.blitzpay.appointments.service.BookAppointmentCommand
import com.elegant.software.blitzpay.appointments.service.BookableTimeService
import com.elegant.software.blitzpay.appointments.service.CancelledBy
import com.elegant.software.blitzpay.appointments.service.RescheduleCommand
import com.elegant.software.blitzpay.appointments.api.AppointmentSchedulingService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

@RestController
@Tag(name = "Appointments", description = "Shopper appointment search, booking, history, cancellation, and rescheduling")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/shopper/appointments", version = "1")
class ShopperAppointmentController(
    private val appointmentService: AppointmentService,
    private val schedulingService: AppointmentSchedulingService,
    private val bookableTimeService: BookableTimeService,
    private val appointmentRepo: AppointmentRepository,
) {
    private val log = LoggerFactory.getLogger(ShopperAppointmentController::class.java)

    @PostMapping("/search")
    fun search(
        @Valid @RequestBody req: BookableTimeRequest,
    ): ResponseEntity<List<BookableSlotResponse>> {
        val branchId = req.branchId!!
        val from = req.from!!.atZone(ZoneId.of("UTC")).toLocalDate()
        val to = req.to!!.atZone(ZoneId.of("UTC")).toLocalDate()

        val context = schedulingService.loadSchedulingContext(branchId, req.employeeId, from, to)
        val existing = appointmentRepo.findAllByBranchIdAndStartTimeBetween(branchId, req.from, req.to)

        val slots = bookableTimeService.compute(
            context = context,
            productCategoryId = req.productCategoryId!!,
            requestedEmployeeId = req.employeeId,
            from = from,
            to = to,
            existingAppointments = existing,
            now = Instant.now(),
        )

        return ResponseEntity.ok(slots.map { it.toResponse() })
    }

    @PostMapping
    fun book(
        @Valid @RequestBody req: BookAppointmentRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<AppointmentResponse> {
        val shopperAccountId = extractSubject(authorization)
        val branchId = req.branchId!!
        val branch = schedulingService.loadSchedulingContext(branchId, null,
            req.startTime!!.atZone(ZoneId.of("UTC")).toLocalDate(),
            req.startTime.atZone(ZoneId.of("UTC")).toLocalDate())

        val appointment = appointmentService.book(
            BookAppointmentCommand(
                merchantId = branch.merchantId,
                branchId = branchId,
                productId = req.productId!!,
                productCategoryId = req.productCategoryId!!,
                startTime = req.startTime,
                employeeId = req.employeeId,
                shopperAccountId = shopperAccountId,
                contactName = req.contactName!!,
                contactEmail = req.contactEmail,
                contactPhone = req.contactPhone,
                note = req.note,
                branchTimezone = branch.branchTimezone.id,
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment.toResponse())
    }

    @GetMapping
    fun history(
        @RequestHeader(name = "Authorization") authorization: String,
    ): ResponseEntity<List<AppointmentResponse>> {
        val shopperAccountId = extractSubject(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val appointments = appointmentService.findByShopperAccountId(shopperAccountId)
        return ResponseEntity.ok(appointments.map { it.toResponse() })
    }

    @DeleteMapping("/{appointmentId}")
    fun cancel(
        @PathVariable appointmentId: UUID,
        @RequestHeader(name = "Authorization") authorization: String,
    ): ResponseEntity<AppointmentResponse> {
        val shopperAccountId = extractSubject(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val updated = appointmentService.cancel(appointmentId, CancelledBy.Shopper(shopperAccountId))
        return ResponseEntity.ok(updated.toResponse())
    }

    @PutMapping("/{appointmentId}/reschedule")
    fun reschedule(
        @PathVariable appointmentId: UUID,
        @Valid @RequestBody req: RescheduleRequest,
        @RequestHeader(name = "Authorization") authorization: String,
    ): ResponseEntity<AppointmentResponse> {
        val shopperAccountId = extractSubject(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val appointment = appointmentService.findById(appointmentId)
        check(appointment.shopperAccountId == shopperAccountId) { "Not your appointment" }

        val tz = schedulingService.loadSchedulingContext(appointment.branchId, null,
            req.newStartTime!!.atZone(ZoneId.of("UTC")).toLocalDate(),
            req.newStartTime.atZone(ZoneId.of("UTC")).toLocalDate()).branchTimezone.id

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

    private fun extractSubject(authorization: String?): String? {
        val token = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.mapCatching { payload ->
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(256)
    }
}
