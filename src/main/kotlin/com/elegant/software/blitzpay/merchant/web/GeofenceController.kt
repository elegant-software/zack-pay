package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.GeofenceRegionsResponse
import com.elegant.software.blitzpay.merchant.application.GeofenceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Geofence", description = "Geofence region discovery for mobile clients")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/geofence", version = "1")
class GeofenceController(private val geofenceService: GeofenceService) {
    private val log = LoggerFactory.getLogger(GeofenceController::class.java)

    @Operation(summary = "List active geofence regions, optionally sorted by distance from caller position")
    @GetMapping("/regions")
    fun regions(
        @RequestParam lat: Double?,
        @RequestParam lng: Double?,
    ): ResponseEntity<GeofenceRegionsResponse> {
        log.info("Geofence regions requested: lat={} lng={}", lat, lng)
        val result = geofenceService.buildRegions(lat, lng)
        log.info("Geofence regions response: count={}", result.regions.size)
        return ResponseEntity.ok(result)
    }
}
