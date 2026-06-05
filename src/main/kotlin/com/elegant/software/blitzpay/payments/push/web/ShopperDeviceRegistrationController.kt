package com.elegant.software.blitzpay.payments.push.web

import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationEntity
import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Base64
import java.util.UUID

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/shopper/devices", version = "1")
class ShopperDeviceRegistrationController(
    private val repo: ShopperDeviceRegistrationRepository,
) {

    @PostMapping
    @Transactional
    fun register(
        @Valid @RequestBody req: ShopperDeviceRequest,
        @RequestHeader(name = "Authorization") authorization: String,
    ): ResponseEntity<ShopperDeviceResponse> {
        val shopperAccountId = extractSubject(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val existing = repo.findByExpoPushToken(req.expoPushToken!!)
        if (existing != null) {
            existing.shopperAccountId.let { /* token already owned */ }
            existing.lastSeenAt = java.time.Instant.now()
            existing.invalid = false
            return ResponseEntity.ok(ShopperDeviceResponse(existing.id, existing.expoPushToken))
        }

        val entity = ShopperDeviceRegistrationEntity(
            shopperAccountId = shopperAccountId,
            expoPushToken = req.expoPushToken,
            platform = req.platform,
        )
        repo.save(entity)
        return ResponseEntity.status(HttpStatus.CREATED).body(ShopperDeviceResponse(entity.id, entity.expoPushToken))
    }

    @DeleteMapping("/{expoPushToken}")
    @Transactional
    fun unregister(
        @PathVariable expoPushToken: String,
        @RequestHeader(name = "Authorization") authorization: String,
    ): ResponseEntity<Void> {
        repo.deleteByExpoPushToken(expoPushToken)
        return ResponseEntity.noContent().build()
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

data class ShopperDeviceRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^ExponentPushToken\\[[^\\]]+\\]$")
    val expoPushToken: String? = null,
    val platform: String? = null,
)

data class ShopperDeviceResponse(val id: UUID, val expoPushToken: String)
