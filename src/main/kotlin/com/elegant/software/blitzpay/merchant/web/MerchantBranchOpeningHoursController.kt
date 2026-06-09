package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.domain.MerchantBranchOpeningException
import com.elegant.software.blitzpay.merchant.domain.MerchantBranchOpeningHours
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchOpeningExceptionRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchOpeningHoursRepository
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
@Tag(name = "Branch Opening Hours", description = "Manage branch recurring opening hours and date-specific exceptions")
@RequestMapping(
    "/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/branches/{branchId}/opening-hours",
    version = "1"
)
class MerchantBranchOpeningHoursController(
    private val openingHoursRepo: MerchantBranchOpeningHoursRepository,
    private val openingExceptionRepo: MerchantBranchOpeningExceptionRepository,
    private val callerResolver: MerchantCallerResolver,
) {

    // ── Recurring weekly windows ────────────────────────────────────────────

    @GetMapping
    fun listWindows(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<List<BranchOpeningWindowResponse>> {
        callerResolver.resolve(auth, merchantId).requireManagerOrEmployee(merchantId)
        return ResponseEntity.ok(
            openingHoursRepo.findAllByBranchId(branchId).map { it.toResponse() }
        )
    }

    @PostMapping
    fun addWindow(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @Valid @RequestBody req: BranchOpeningWindowRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<BranchOpeningWindowResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val window = MerchantBranchOpeningHours(
            branchId = branchId,
            dayOfWeek = req.dayOfWeek!!,
            startTime = req.startTime!!,
            endTime = req.endTime!!,
        )
        check(window.startTime.isBefore(window.endTime)) { "startTime must be before endTime" }
        return ResponseEntity.status(HttpStatus.CREATED).body(openingHoursRepo.save(window).toResponse())
    }

    @DeleteMapping("/{windowId}")
    fun removeWindow(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable windowId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<Void> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val window = openingHoursRepo.findById(windowId)
            .orElseThrow { NoSuchElementException("Opening hours window $windowId not found") }
        check(window.branchId == branchId) { "Window does not belong to this branch" }
        openingHoursRepo.delete(window)
        return ResponseEntity.noContent().build()
    }

    // ── Date-specific exceptions ────────────────────────────────────────────

    @GetMapping("/exceptions")
    fun listExceptions(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<List<BranchOpeningExceptionResponse>> {
        callerResolver.resolve(auth, merchantId).requireManagerOrEmployee(merchantId)
        val exceptions = openingExceptionRepo.findAllByBranchIdAndExceptionDateBetween(
            branchId, LocalDate.now(), LocalDate.now().plusYears(1)
        )
        return ResponseEntity.ok(exceptions.map { it.toResponse() })
    }

    @PostMapping("/exceptions")
    fun addException(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @Valid @RequestBody req: BranchOpeningExceptionRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<BranchOpeningExceptionResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        if (req.type == MerchantBranchOpeningException.EXTRA_WINDOW) {
            check(req.startTime != null && req.endTime != null) { "startTime and endTime required for EXTRA_WINDOW" }
            check(req.startTime.isBefore(req.endTime)) { "startTime must be before endTime" }
        }
        val exception = MerchantBranchOpeningException(
            branchId = branchId,
            exceptionDate = req.date!!,
            type = req.type!!,
            startTime = req.startTime,
            endTime = req.endTime,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(openingExceptionRepo.save(exception).toResponse())
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    fun removeException(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable exceptionId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<Void> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val exception = openingExceptionRepo.findById(exceptionId)
            .orElseThrow { NoSuchElementException("Opening exception $exceptionId not found") }
        check(exception.branchId == branchId) { "Exception does not belong to this branch" }
        openingExceptionRepo.delete(exception)
        return ResponseEntity.noContent().build()
    }
}

// ── DTOs ────────────────────────────────────────────────────────────────────

data class BranchOpeningWindowRequest(
    @field:NotNull @field:Min(1) @field:Max(7) val dayOfWeek: Int?,
    @field:NotNull val startTime: LocalTime?,
    @field:NotNull val endTime: LocalTime?,
)

data class BranchOpeningExceptionRequest(
    @field:NotNull val date: LocalDate?,
    @field:NotBlank val type: String?,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
)

data class BranchOpeningWindowResponse(
    val id: UUID, val branchId: UUID,
    val dayOfWeek: Int, val startTime: LocalTime, val endTime: LocalTime,
    val createdAt: Instant, val updatedAt: Instant,
)

data class BranchOpeningExceptionResponse(
    val id: UUID, val branchId: UUID,
    val date: LocalDate, val type: String,
    val startTime: LocalTime?, val endTime: LocalTime?,
    val createdAt: Instant,
)

fun MerchantBranchOpeningHours.toResponse() = BranchOpeningWindowResponse(
    id, branchId, dayOfWeek, startTime, endTime, createdAt, updatedAt
)
fun MerchantBranchOpeningException.toResponse() = BranchOpeningExceptionResponse(
    id, branchId, exceptionDate, type, startTime, endTime, createdAt
)
