package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.SaveStorefrontThemeRequest
import com.elegant.software.blitzpay.merchant.api.StorefrontThemeResponse
import com.elegant.software.blitzpay.merchant.iam.web.PlatformAdminAccess
import com.elegant.software.blitzpay.merchant.service.StorefrontThemeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Tag(name = "Storefront Themes", description = "Named storefront looks merchants pick from; maintained by platform admins")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/storefront/themes", version = "1")
class StorefrontThemeController(private val service: StorefrontThemeService) {

    @Operation(summary = "List the published themes a merchant can pick; includeDrafts=true adds drafts for preview")
    @GetMapping
    fun list(@RequestParam(defaultValue = "false") includeDrafts: Boolean): List<StorefrontThemeResponse> =
        service.list(includeDrafts)

    @Operation(summary = "Get one theme, including drafts, for storefront preview")
    @GetMapping("/{code}")
    fun get(@PathVariable code: String): StorefrontThemeResponse = service.get(code)

    @Operation(summary = "Create or replace a draft theme (platform admin)")
    @PutMapping("/{code}")
    fun save(
        @PathVariable code: String,
        @RequestBody request: SaveStorefrontThemeRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): StorefrontThemeResponse {
        if (!PlatformAdminAccess.isPlatformAdmin(jwt)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Platform admin only")
        return service.save(code, request)
    }
}
