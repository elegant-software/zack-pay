package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.BrandingSettings
import com.elegant.software.blitzpay.merchant.api.MerchantLogoUploadRequest
import com.elegant.software.blitzpay.merchant.iam.web.PlatformAdminAccess
import com.elegant.software.blitzpay.merchant.service.MerchantBrandingService
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.bind.MethodArgumentNotValidException
import java.util.UUID

@RestController
@Tag(name = "Merchant Branding", description = "Merchant and branch branding with Simpleio defaults; writes require the blitzpay-admin realm role")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants", version = "1")
class MerchantBrandingController(private val service: MerchantBrandingService) {
    @GetMapping("/{merchantId}/branding", "/{merchantId}/branches/{branchId}/branding")
    fun get(@PathVariable merchantId: UUID, @PathVariable(required = false) branchId: UUID?) =
        service.get(merchantId, branchId)

    @PutMapping("/{merchantId}/branding", "/{merchantId}/branches/{branchId}/branding")
    fun replace(
        @PathVariable merchantId: UUID,
        @PathVariable(required = false) branchId: UUID?,
        @AuthenticationPrincipal jwt: Jwt?,
        @Valid @RequestBody settings: BrandingSettings,
    ) = admin(jwt).let { service.replace(merchantId, branchId, settings) }

    @PostMapping("/{merchantId}/branding/logo/upload-url", "/{merchantId}/branches/{branchId}/branding/logo/upload-url")
    fun upload(
        @PathVariable merchantId: UUID,
        @PathVariable(required = false) branchId: UUID?,
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody request: MerchantLogoUploadRequest,
    ) = admin(jwt).let { service.createUploadUrl(merchantId, branchId, request.contentType) }

    private fun admin(jwt: Jwt?) {
        if (!PlatformAdminAccess.isPlatformAdmin(jwt)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator role required")
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidSettings(ex: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid branding settings")
        problem.setProperty("errors", ex.bindingResult.fieldErrors.groupBy({ it.field }, { it.defaultMessage ?: "Invalid value" }))
        return ResponseEntity.badRequest().body(problem)
    }
}
