package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.AppearanceResponse
import com.elegant.software.blitzpay.merchant.api.CreateAppearanceRequest
import com.elegant.software.blitzpay.merchant.api.CurrentlyHappeningResponse
import com.elegant.software.blitzpay.merchant.api.PushLivePositionRequest
import com.elegant.software.blitzpay.merchant.application.MerchantAppearanceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
import java.util.UUID

@Tag(name = "Appearances", description = "Manage scheduled location appearances for MOBILE merchant branches")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/branches/{branchId}/appearances", version = "1")
class MerchantAppearanceController(private val appearanceService: MerchantAppearanceService) {

    @Operation(summary = "Create a new appearance for a MOBILE branch")
    @PostMapping
    fun create(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestBody request: CreateAppearanceRequest,
    ): ResponseEntity<AppearanceResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(appearanceService.create(merchantId, branchId, request))

    @Operation(summary = "List all appearances for a branch")
    @GetMapping
    fun list(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
    ): ResponseEntity<List<AppearanceResponse>> =
        ResponseEntity.ok(appearanceService.list(merchantId, branchId))

    @Operation(summary = "Get an appearance by ID")
    @GetMapping("/{appearanceId}")
    fun get(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable appearanceId: UUID,
    ): ResponseEntity<AppearanceResponse> =
        ResponseEntity.ok(appearanceService.get(merchantId, branchId, appearanceId))

    @Operation(summary = "Cancel (delete) an appearance")
    @DeleteMapping("/{appearanceId}")
    fun delete(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable appearanceId: UUID,
    ): ResponseEntity<Void> {
        appearanceService.delete(merchantId, branchId, appearanceId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Push a live GPS position to an active appearance")
    @PutMapping("/{appearanceId}/live-position")
    fun pushLivePosition(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable appearanceId: UUID,
        @RequestBody request: PushLivePositionRequest,
    ): ResponseEntity<AppearanceResponse> =
        ResponseEntity.ok(appearanceService.pushLivePosition(merchantId, branchId, appearanceId, request))
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
