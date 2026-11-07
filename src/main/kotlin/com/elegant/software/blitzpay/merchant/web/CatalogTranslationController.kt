package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.CatalogTranslationResponse
import com.elegant.software.blitzpay.merchant.api.TranslationCoverageResponse
import com.elegant.software.blitzpay.merchant.api.UpsertCatalogTranslationRequest
import com.elegant.software.blitzpay.merchant.service.CatalogTranslationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID


@Tag(name = "Merchant Catalog Translations", description = "Product and category translations for the International Menu")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}", version = "1")
class CatalogTranslationController(private val catalogTranslationService: CatalogTranslationService) {

    @Operation(summary = "List a product's translations, including stale ones")
    @GetMapping("/products/{productId}/translations")
    fun listProductTranslations(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
    ): ResponseEntity<List<CatalogTranslationResponse>> =
        ResponseEntity.ok(catalogTranslationService.productTranslations(merchantId, productId))

    @Operation(summary = "Create or replace a product translation as the merchant")
    @PutMapping("/products/{productId}/translations/{language}")
    fun putProductTranslation(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @PathVariable language: String,
        @RequestBody request: UpsertCatalogTranslationRequest,
    ): ResponseEntity<CatalogTranslationResponse> =
        ResponseEntity.ok(catalogTranslationService.putProductTranslation(merchantId, productId, language, request))

    @Operation(summary = "Delete a product translation")
    @DeleteMapping("/products/{productId}/translations/{language}")
    fun deleteProductTranslation(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @PathVariable language: String,
    ): ResponseEntity<Void> {
        catalogTranslationService.deleteProductTranslation(merchantId, productId, language)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List a product category's translations, including stale ones")
    @GetMapping("/product-categories/{categoryId}/translations")
    fun listCategoryTranslations(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
    ): ResponseEntity<List<CatalogTranslationResponse>> =
        ResponseEntity.ok(catalogTranslationService.categoryTranslations(merchantId, categoryId))

    @Operation(summary = "Create or replace a product category translation as the merchant")
    @PutMapping("/product-categories/{categoryId}/translations/{language}")
    fun putCategoryTranslation(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
        @PathVariable language: String,
        @RequestBody request: UpsertCatalogTranslationRequest,
    ): ResponseEntity<CatalogTranslationResponse> =
        ResponseEntity.ok(catalogTranslationService.putCategoryTranslation(merchantId, categoryId, language, request))

    @Operation(summary = "Delete a product category translation")
    @DeleteMapping("/product-categories/{categoryId}/translations/{language}")
    fun deleteCategoryTranslation(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
        @PathVariable language: String,
    ): ResponseEntity<Void> {
        catalogTranslationService.deleteCategoryTranslation(merchantId, categoryId, language)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Translation Coverage per Supported Menu Language")
    @GetMapping("/translation-coverage")
    fun coverage(@PathVariable merchantId: UUID): ResponseEntity<List<TranslationCoverageResponse>> =
        ResponseEntity.ok(catalogTranslationService.coverage(merchantId))
}
