package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MerchantThemeRequest
import com.elegant.software.blitzpay.merchant.api.ThemeApp
import com.elegant.software.blitzpay.merchant.iam.web.PlatformAdminAccess
import com.elegant.software.blitzpay.merchant.service.MerchantThemeService
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@Tag(name = "Merchant Themes", description = "Saved merchant color palettes; requires the blitzpay-admin realm role")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/themes", version = "1")
class MerchantThemeController(private val service: MerchantThemeService) {
    @GetMapping
    @Operation(summary = "List saved merchant themes", operationId = "listMerchantThemes")
    fun list(@PathVariable merchantId: UUID, @AuthenticationPrincipal jwt: Jwt?,
        @RequestParam(defaultValue = "SHOPPER") app: ThemeApp) =
        admin(jwt).let { service.list(merchantId, app) }

    @PostMapping
    @Operation(summary = "Create a named merchant theme", operationId = "createMerchantTheme")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable merchantId: UUID, @AuthenticationPrincipal jwt: Jwt?,
        @Valid @RequestBody request: MerchantThemeRequest,
        @RequestParam(defaultValue = "SHOPPER") app: ThemeApp) =
        admin(jwt).let { service.create(merchantId, request, app) }

    @PutMapping("/{themeId}")
    @Operation(summary = "Replace a saved merchant theme", operationId = "updateMerchantTheme")
    fun update(@PathVariable merchantId: UUID, @PathVariable themeId: UUID,
        @AuthenticationPrincipal jwt: Jwt?, @Valid @RequestBody request: MerchantThemeRequest,
        @RequestParam(defaultValue = "SHOPPER") app: ThemeApp) =
        admin(jwt).let { service.update(merchantId, themeId, request, app) }

    private fun admin(jwt: Jwt?) {
        if (!PlatformAdminAccess.isPlatformAdmin(jwt)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator role required")
        }
    }
}
