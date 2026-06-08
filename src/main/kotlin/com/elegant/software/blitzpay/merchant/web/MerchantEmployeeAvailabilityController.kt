package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeAvailability
import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeAvailabilityException
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeAvailabilityExceptionRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeAvailabilityRepository
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@RestController
@Tag(name = "Employee Availability", description = "Manage employee recurring availability and date-specific exceptions")
@RequestMapping(
    "/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/employees/{employeeId}/branches/{branchId}/availability",
    version = "1"
)
class MerchantEmployeeAvailabilityController(
    private val availabilityRepo: MerchantEmployeeAvailabilityRepository,
    private val exceptionRepo: MerchantEmployeeAvailabilityExceptionRepository,
    private val callerResolver: MerchantCallerResolver,
) {

    // ── Recurring weekly windows ────────────────────────────────────────────

    @GetMapping
    fun listWindows(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<List<AvailabilityWindowResponse>> {
        callerResolver.resolve(auth, merchantId).requireManagerOrEmployee(merchantId)
        return ResponseEntity.ok(
            availabilityRepo.findAllByEmployeeIdAndBranchId(employeeId, branchId).map { it.toResponse() }
        )
    }

    @PostMapping
    fun addWindow(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @PathVariable branchId: UUID,
        @Valid @RequestBody req: AvailabilityWindowRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<AvailabilityWindowResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val window = MerchantEmployeeAvailability(
            employeeId = employeeId,
            branchId = branchId,
            dayOfWeek = req.dayOfWeek!!,
            startTime = req.startTime!!,
            endTime = req.endTime!!,
        )
        check(window.startTime.isBefore(window.endTime)) { "startTime must be before endTime" }
        return ResponseEntity.status(HttpStatus.CREATED).body(availabilityRepo.save(window).toResponse())
    }

    @DeleteMapping("/{availabilityId}")
    fun removeWindow(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable availabilityId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<Void> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val window = availabilityRepo.findById(availabilityId)
            .orElseThrow { NoSuchElementException("Availability window $availabilityId not found") }
        check(window.employeeId == employeeId && window.branchId == branchId) { "Window does not belong to this employee/branch" }
        availabilityRepo.delete(window)
        return ResponseEntity.noContent().build()
    }

    // ── Date-specific exceptions ────────────────────────────────────────────

    @GetMapping("/exceptions")
    fun listExceptions(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<List<AvailabilityExceptionResponse>> {
        callerResolver.resolve(auth, merchantId).requireManagerOrEmployee(merchantId)
        val exceptions = exceptionRepo.findAllByEmployeeIdAndBranchIdAndExceptionDateBetween(
            employeeId, branchId, LocalDate.now(), LocalDate.now().plusYears(1)
        )
        return ResponseEntity.ok(exceptions.map { it.toResponse() })
    }

    @PostMapping("/exceptions")
    fun addException(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @PathVariable branchId: UUID,
        @Valid @RequestBody req: AvailabilityExceptionRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<AvailabilityExceptionResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        if (req.type == MerchantEmployeeAvailabilityException.EXTRA_WINDOW) {
            check(req.startTime != null && req.endTime != null) { "startTime and endTime required for EXTRA_WINDOW" }
            check(req.startTime.isBefore(req.endTime)) { "startTime must be before endTime" }
        }
        val exception = MerchantEmployeeAvailabilityException(
            employeeId = employeeId,
            branchId = branchId,
            exceptionDate = req.date!!,
            type = req.type!!,
            startTime = req.startTime,
            endTime = req.endTime,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(exceptionRepo.save(exception).toResponse())
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    fun removeException(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable exceptionId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<Void> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val exception = exceptionRepo.findById(exceptionId)
            .orElseThrow { NoSuchElementException("Availability exception $exceptionId not found") }
        check(exception.employeeId == employeeId && exception.branchId == branchId) { "Exception does not belong to this employee/branch" }
        exceptionRepo.delete(exception)
        return ResponseEntity.noContent().build()
    }
}

// ── DTOs ────────────────────────────────────────────────────────────────────

data class AvailabilityWindowRequest(
    @field:NotNull @field:Min(1) @field:Max(7) val dayOfWeek: Int?,
    @field:NotNull val startTime: LocalTime?,
    @field:NotNull val endTime: LocalTime?,
)

data class AvailabilityExceptionRequest(
    @field:NotNull val date: LocalDate?,
    @field:NotBlank val type: String?,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
)

data class AvailabilityWindowResponse(
    val id: UUID, val employeeId: UUID, val branchId: UUID,
    val dayOfWeek: Int, val startTime: LocalTime, val endTime: LocalTime,
    val createdAt: Instant, val updatedAt: Instant,
)

data class AvailabilityExceptionResponse(
    val id: UUID, val employeeId: UUID, val branchId: UUID,
    val date: LocalDate, val type: String,
    val startTime: LocalTime?, val endTime: LocalTime?,
    val createdAt: Instant,
)

fun MerchantEmployeeAvailability.toResponse() = AvailabilityWindowResponse(
    id, employeeId, branchId, dayOfWeek, startTime, endTime, createdAt, updatedAt
)
fun MerchantEmployeeAvailabilityException.toResponse() = AvailabilityExceptionResponse(
    id, employeeId, branchId, exceptionDate, type, startTime, endTime, createdAt
)
