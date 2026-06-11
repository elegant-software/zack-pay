package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.AppearanceHistoryPageResponse
import com.elegant.software.blitzpay.merchant.api.AppearanceHistoryStatus
import com.elegant.software.blitzpay.merchant.api.AppearanceResponse
import com.elegant.software.blitzpay.merchant.api.CreateAppearanceRequest
import com.elegant.software.blitzpay.merchant.api.CurrentlyHappeningResponse
import com.elegant.software.blitzpay.merchant.application.MerchantAppearanceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Tag(name = "Appearances", description = "Manage scheduled location appearances for MOBILE merchants")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/branches/{branchId}/appearances", version = "1")
class MerchantAppearanceController(
    private val appearanceService: MerchantAppearanceService,
    private val callerResolver: MerchantCallerResolver,
) {

    @Operation(summary = "Create a new appearance for a MOBILE branch")
    @PostMapping
    fun create(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestBody request: CreateAppearanceRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<AppearanceResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(appearanceService.create(merchantId, branchId, request))
    }

    @Operation(summary = "List active and upcoming appearances for a branch")
    @GetMapping
    fun list(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<List<AppearanceResponse>> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        return ResponseEntity.ok(appearanceService.list(merchantId, branchId))
    }

    @Operation(summary = "List paginated appearance history for a branch")
    @GetMapping("/history")
    fun history(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant,
        @RequestParam(required = false) status: AppearanceHistoryStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<AppearanceHistoryPageResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        return ResponseEntity.ok(appearanceService.branchHistory(merchantId, branchId, from, to, status, page, size))
    }

    @Operation(summary = "Get an appearance by ID")
    @GetMapping("/{appearanceId}")
    fun get(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable appearanceId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<AppearanceResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        return ResponseEntity.ok(appearanceService.get(merchantId, branchId, appearanceId))
    }

    @Operation(summary = "Cancel (delete) an appearance")
    @DeleteMapping("/{appearanceId}")
    fun delete(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable appearanceId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<Void> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        appearanceService.delete(merchantId, branchId, appearanceId)
        return ResponseEntity.noContent().build()
    }

}

@Tag(name = "Appearances", description = "Admin appearance history across a MOBILE merchant")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/appearances", version = "1")
class MerchantAppearanceHistoryController(private val appearanceService: MerchantAppearanceService) {

    @Operation(summary = "List active and upcoming appearances across all merchant branches")
    @GetMapping("/operational")
    fun operational(
        @PathVariable merchantId: UUID,
    ): ResponseEntity<List<AppearanceResponse>> =
        ResponseEntity.ok(appearanceService.merchantOperational(merchantId))

    @Operation(summary = "List paginated appearance history across merchant branches")
    @GetMapping("/history")
    fun history(
        @PathVariable merchantId: UUID,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant,
        @RequestParam(required = false) status: AppearanceHistoryStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<AppearanceHistoryPageResponse> =
        ResponseEntity.ok(appearanceService.merchantHistory(merchantId, branchId, from, to, status, page, size))
}

@Tag(name = "Appearances", description = "Discover currently active mobile merchant appearances")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/appearances", version = "1")
class AppearanceDiscoveryController(private val appearanceService: MerchantAppearanceService) {

    @Operation(
        summary = "Get all currently happening appearances",
        description = "Returns all appearances whose time window is active now. " +
            "Optionally filtered by proximity when latitude, longitude, and radiusMeters are provided."
    )
    @GetMapping("/currently-happening")
    fun currentlyHappening(
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) radiusMeters: Double?,
    ): ResponseEntity<CurrentlyHappeningResponse> =
        ResponseEntity.ok(appearanceService.currentlyHappening(latitude, longitude, radiusMeters))
}
