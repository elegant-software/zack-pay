package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MerchantLocationResponse
import com.elegant.software.blitzpay.merchant.api.NearbyMerchantsResponse
import com.elegant.software.blitzpay.merchant.api.SetMerchantLocationRequest
import com.elegant.software.blitzpay.merchant.application.MerchantLocationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Merchant Location", description = "Geolocation and geofencing for merchants")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants", version = "1")
class MerchantLocationController(private val merchantLocationService: MerchantLocationService) {
    private val log = LoggerFactory.getLogger(MerchantLocationController::class.java)

    @Operation(summary = "Set or update a merchant's location and geofence radius")
    @PutMapping("/{merchantId}/location")
    fun setLocation(
        @PathVariable merchantId: UUID,
        @RequestBody request: SetMerchantLocationRequest,
    ): ResponseEntity<MerchantLocationResponse> =
        ResponseEntity.ok(merchantLocationService.setLocation(merchantId, request))

    @Operation(summary = "Get a merchant's current location")
    @GetMapping("/{merchantId}/location")
    fun getLocation(@PathVariable merchantId: UUID): ResponseEntity<MerchantLocationResponse> =
        ResponseEntity.ok(merchantLocationService.getLocation(merchantId))

    @Operation(summary = "Remove a merchant's location")
    @DeleteMapping("/{merchantId}/location")
    fun deleteLocation(@PathVariable merchantId: UUID): ResponseEntity<Void> {
        merchantLocationService.deleteLocation(merchantId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Find merchants near a position")
    @GetMapping("/nearby")
    fun findNearby(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "500") radiusMeters: Double,
    ): ResponseEntity<NearbyMerchantsResponse> {
        log.info("Nearby merchants requested: lat={} lng={} radiusMeters={}", lat, lng, radiusMeters)
        val result = merchantLocationService.findNearby(lat, lng, radiusMeters)
        log.info("Nearby merchants response: merchantCount={}", result.merchants.size)
        return ResponseEntity.ok(result)
    }
}
