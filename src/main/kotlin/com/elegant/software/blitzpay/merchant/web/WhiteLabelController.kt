package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.*
import com.elegant.software.blitzpay.merchant.iam.web.PlatformAdminAccess
import com.elegant.software.blitzpay.merchant.service.WhiteLabelService
import com.elegant.software.blitzpay.merchant.service.WhiteLabelConflictException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.http.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.Operation

@RestController
@Tag(name = "Merchant White Label", description = "App-scoped branding and semantic palettes. Platform admins and merchant managers can publish; only resolved storefront configuration is public.")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants", version = "1")
class WhiteLabelController(private val service: WhiteLabelService, private val callers: MerchantCallerResolver,
    private val branding: com.elegant.software.blitzpay.merchant.service.MerchantBrandingService) {
    @GetMapping("/{merchantId}/white-label", "/{merchantId}/branches/{branchId}/white-label")
    @Operation(operationId = "getWhiteLabelSettings", summary = "Read app-scoped white-label editor settings")
    fun get(@PathVariable merchantId: UUID, @PathVariable(required = false) branchId: UUID?,
        @RequestParam(defaultValue = "SHOPPER") app: ThemeApp,
        @AuthenticationPrincipal jwt: Jwt?, @RequestHeader("Authorization", required = false) authorization: String?) : ResponseEntity<WhiteLabelResponse> {
        requireEditor(merchantId, jwt, authorization)
        return noStore(service.get(merchantId, branchId, app))
    }

    @GetMapping("/{merchantId}/white-label/effective", "/{merchantId}/branches/{branchId}/white-label/effective")
    @Operation(operationId = "getEffectiveWhiteLabel", summary = "Read resolved app branding without editor data")
    fun effective(@PathVariable merchantId: UUID, @PathVariable(required = false) branchId: UUID?,
        @RequestParam(defaultValue = "SHOPPER") app: ThemeApp, @AuthenticationPrincipal jwt: Jwt?): ResponseEntity<EffectiveWhiteLabel> {
        if (app != ThemeApp.STOREFRONT && jwt == null) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required for this app")
        return noStore(service.get(merchantId, branchId, app).effective)
    }

    @PostMapping("/{merchantId}/white-label/logo/upload-url", "/{merchantId}/branches/{branchId}/white-label/logo/upload-url")
    @Operation(operationId = "createWhiteLabelLogoUpload", summary = "Create a unique merchant or branch logo upload")
    fun upload(@PathVariable merchantId: UUID, @PathVariable(required = false) branchId: UUID?,
        @RequestBody request: MerchantLogoUploadRequest, @AuthenticationPrincipal jwt: Jwt?,
        @RequestHeader("Authorization", required = false) authorization: String?): ResponseEntity<MerchantLogoUploadResponse> {
        requireEditor(merchantId, jwt, authorization)
        return noStore(branding.createUploadUrl(merchantId, branchId, request.contentType))
    }

    @PutMapping("/{merchantId}/white-label", "/{merchantId}/branches/{branchId}/white-label")
    @Operation(operationId = "publishWhiteLabelSettings", summary = "Publish app branding with an optimistic revision check")
    fun replace(@PathVariable merchantId: UUID, @PathVariable(required = false) branchId: UUID?,
        @RequestParam(defaultValue = "SHOPPER") app: ThemeApp, @RequestBody update: WhiteLabelUpdate,
        @AuthenticationPrincipal jwt: Jwt?, @RequestHeader("Authorization", required = false) authorization: String?): ResponseEntity<WhiteLabelResponse> {
        requireEditor(merchantId, jwt, authorization)
        return noStore(service.replace(merchantId, branchId, app, update))
    }

    private fun requireEditor(merchantId: UUID, jwt: Jwt?, authorization: String?) {
        if (PlatformAdminAccess.isPlatformAdmin(jwt)) return
        val manager = runCatching { callers.resolve(authorization, merchantId).isManagerFor(merchantId) }.getOrDefault(false)
        if (!manager) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant manager or platform administrator required")
    }

    private fun <T : Any> noStore(value: T) = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value)

    @ExceptionHandler(WhiteLabelConflictException::class, ObjectOptimisticLockingFailureException::class, DataIntegrityViolationException::class)
    fun conflict(): ResponseEntity<ProblemDetail> = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "White-label settings changed. Reload before saving."))

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> = ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid white-label settings"))

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun malformed(): ResponseEntity<ProblemDetail> = ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Invalid white-label request. Supply complete light and dark palettes, valid themeMode and app values, and a settings object."))
}
