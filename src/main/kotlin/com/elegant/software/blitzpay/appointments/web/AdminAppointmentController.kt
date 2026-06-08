package com.elegant.software.blitzpay.appointments.web

import com.elegant.software.blitzpay.appointments.service.AppointmentService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@Tag(name = "Admin Appointments", description = "Support-facing branch calendar and appointment override")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/admin/branches/{branchId}/appointments", version = "1")
class AdminAppointmentController(
    private val appointmentService: AppointmentService,
) {
    /** Branch Calendar — admin view of all appointments for a branch in a time window. */
    @GetMapping
    fun branchCalendar(
        @PathVariable branchId: UUID,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
    ): ResponseEntity<List<AppointmentResponse>> {
        val appointments = appointmentService.findByBranch(branchId, from, to)
        return ResponseEntity.ok(appointments.map { it.toResponse() })
    }
}
