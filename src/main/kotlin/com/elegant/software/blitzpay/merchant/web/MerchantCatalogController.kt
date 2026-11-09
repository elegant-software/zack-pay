package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.api.BranchCatalogCategories
import com.elegant.software.blitzpay.merchant.api.CatalogProductPage
import com.elegant.software.blitzpay.merchant.api.CatalogThumbnailBatch
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.web.PlatformAdminAccess
import com.elegant.software.blitzpay.merchant.service.MenuLanguageService
import com.elegant.software.blitzpay.merchant.service.MerchantProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Merchant Catalog", description = "Branch-scoped catalog browsing for mobile apps")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/catalog", version = "1")
class MerchantCatalogController(
    private val productService: MerchantProductService,
    private val menuLanguageService: MenuLanguageService,
    private val callerResolver: MerchantCallerResolver,
) {
    @Operation(
        summary = "Read a page of branch products",
        description = "Stable oldest-first creation order with product ID as tie-breaker. Pass nextCursor unchanged for the next page. " +
            "Cursors are scoped to merchant, branch, category and visibility. Defaults to active products only. " +
            "imageUrl is the larger detail image; thumbnailUrl is optional and intended for lists.",
    )
    @GetMapping("/products")
    fun products(
        @PathVariable merchantId: UUID,
        @RequestParam branchId: UUID,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(defaultValue = "false") uncategorized: Boolean,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @RequestParam(defaultValue = "24") limit: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) language: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CatalogProductPage {
        requireInactiveAccess(merchantId, branchId, includeInactive, jwt)
        return productService.catalogPage(
            merchantId, branchId, categoryId, uncategorized, includeInactive, limit, cursor,
            menuLanguageService.resolve(merchantId, language, acceptLanguage),
        )
    }

    @Operation(summary = "Read nonempty branch categories and product counts")
    @GetMapping("/categories")
    fun categories(
        @PathVariable merchantId: UUID,
        @RequestParam branchId: UUID,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @RequestParam(required = false) language: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): BranchCatalogCategories {
        requireInactiveAccess(merchantId, branchId, includeInactive, jwt)
        return productService.catalogCategories(
            merchantId, branchId, includeInactive,
            menuLanguageService.resolve(merchantId, language, acceptLanguage),
        )
    }

    @Operation(summary = "Generate missing thumbnails for a batch of existing branch products",
        description = "Authorized branch staff only. Processes at most 24 products per call. Follow nextCursor until hasMore is false. " +
            "JPEG/PNG sources are resized; originals are never replaced. Existing thumbnails and unsupported formats are skipped.")
    @PostMapping("/thumbnails")
    fun thumbnails(
        @PathVariable merchantId: UUID,
        @RequestParam branchId: UUID,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(required = false) cursor: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CatalogThumbnailBatch {
        requireInactiveAccess(merchantId, branchId, true, jwt)
        return productService.backfillCatalogThumbnails(merchantId, branchId, limit, cursor)
    }

    private fun requireInactiveAccess(merchantId: UUID, branchId: UUID, includeInactive: Boolean, jwt: Jwt?) {
        if (!includeInactive) return
        if (PlatformAdminAccess.isPlatformAdmin(jwt)) return
        val branchGroup = KeycloakGroupNames.branchGroup(branchId)
        if (jwt?.getClaimAsStringList("groups").orEmpty().any {
                it.substringAfterLast('/').equals(branchGroup, ignoreCase = true)
            }) return
        val assignments = jwt?.let {
            try {
                callerResolver.resolve("Bearer ${it.tokenValue}", merchantId).assignments
            } catch (_: IllegalStateException) {
                throw ApiException(HttpStatus.FORBIDDEN, "Only authorized branch staff may view inactive catalog products", "CATALOG_ACCESS_DENIED")
            }
        }.orEmpty()
        if (assignments.any {
                it.merchantId == merchantId &&
                    (it.role == MerchantOperationsRole.MERCHANT_MANAGER || it.branchId == null || it.branchId == branchId)
            }) return
        throw ApiException(HttpStatus.FORBIDDEN, "Only authorized branch staff may view inactive catalog products", "CATALOG_ACCESS_DENIED")
    }
}
