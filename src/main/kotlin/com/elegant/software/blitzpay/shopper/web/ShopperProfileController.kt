package com.elegant.software.blitzpay.shopper.web

import com.elegant.software.blitzpay.shopper.api.ShopperProfileData
import com.elegant.software.blitzpay.shopper.service.ShopperProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Base64

@Tag(name = "Shopper Profile", description = "Shopper invoice address — used automatically as Buyer Contact on orders")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/shopper/profile", version = "1")
class ShopperProfileController(private val service: ShopperProfileService) {

    @Operation(summary = "Get the authenticated shopper's invoice address")
    @GetMapping
    fun get(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<ShopperProfileResponse> {
        val shopperId = requireSubject(authorization)
        val profile = service.get(shopperId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(profile.toResponse())
    }

    @Operation(summary = "Create or update the authenticated shopper's invoice address")
    @PutMapping
    fun upsert(
        @RequestBody request: UpsertShopperProfileRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<ShopperProfileResponse> {
        require(request.name.isNotBlank()) { "name must not be blank" }
        require(request.street.isNotBlank()) { "street must not be blank" }
        require(request.zip.isNotBlank()) { "zip must not be blank" }
        require(request.city.isNotBlank()) { "city must not be blank" }
        require(request.country.isNotBlank()) { "country must not be blank" }
        val shopperId = requireSubject(authorization)
        return ResponseEntity.ok(service.upsert(shopperId, request).toResponse())
    }

    @Operation(summary = "Delete the authenticated shopper's invoice address")
    @DeleteMapping
    fun delete(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<Void> {
        val shopperId = requireSubject(authorization)
        service.delete(shopperId)
        return ResponseEntity.noContent().build()
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Invalid request")))

    private fun requireSubject(authorization: String?): String =
        extractSubject(authorization) ?: throw org.springframework.web.server.ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Valid authentication is required"
        )

    private fun extractSubject(authorization: String?): String? {
        val token = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.mapCatching { payload ->
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(512)
    }

    private fun ShopperProfileData.toResponse() = ShopperProfileResponse(
        name = name,
        street = street,
        zip = zip,
        city = city,
        country = country,
        email = email,
    )
}
