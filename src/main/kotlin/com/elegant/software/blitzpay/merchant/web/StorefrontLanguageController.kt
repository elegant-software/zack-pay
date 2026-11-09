package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.StorefrontLanguageResponse
import com.elegant.software.blitzpay.merchant.service.StorefrontLanguageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Read-only guest bootstrap, available before an order-scoped guest token exists. */
@Tag(name = "Storefront Languages", description = "Public interface language metadata")
@RestController
@RequestMapping("/v1/storefront/languages")
class StorefrontLanguageController(private val languages: StorefrontLanguageService) {
    @Operation(summary = "List storefront interface languages")
    @GetMapping
    fun list(): List<StorefrontLanguageResponse> = languages.catalogue()
}
