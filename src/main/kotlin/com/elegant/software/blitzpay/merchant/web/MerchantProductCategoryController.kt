package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.service.MenuLanguageService
import com.elegant.software.blitzpay.merchant.service.MerchantProductCategoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import com.elegant.software.blitzpay.merchant.service.ProductImageUpload
import org.springframework.web.bind.annotation.*
import java.util.UUID


@Suppress("unused")
@Tag(name = "Merchant Product Categories", description = "Manage merchant product categories")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/product-categories", version = "1")
class MerchantProductCategoryController(
    private val merchantProductCategoryService: MerchantProductCategoryService,
    private val menuLanguageService: MenuLanguageService,
) {

    @Operation(summary = "Create a product category")
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @PathVariable merchantId: UUID,
        @RequestBody request: CreateProductCategoryRequest,
    ): ResponseEntity<ProductCategoryResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(merchantProductCategoryService.create(merchantId, request))

    @Operation(summary = "Create a product category with an optional image")
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createWithImage(
        @PathVariable merchantId: UUID,
        @RequestPart("category") request: CreateProductCategoryRequest,
        @RequestPart("image", required = false) image: MultipartFile?,
    ): ResponseEntity<ProductCategoryResponse> = ResponseEntity.status(HttpStatus.CREATED)
        .body(merchantProductCategoryService.create(merchantId, request, image?.toImageUpload()))

    @Operation(summary = "List all product categories")
    @GetMapping
    fun list(
        @PathVariable merchantId: UUID,
        @RequestParam("language", required = false) language: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<List<ProductCategoryResponse>> =
        ResponseEntity.ok(
            merchantProductCategoryService.list(merchantId, menuLanguageService.resolve(merchantId, language, acceptLanguage))
        )

    @Operation(summary = "Update a product category")
    @PutMapping("/{categoryId}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
        @RequestBody request: UpdateProductCategoryRequest,
    ): ResponseEntity<ProductCategoryResponse> =
        ResponseEntity.ok(merchantProductCategoryService.update(merchantId, categoryId, request))

    @Operation(summary = "Update a product category and optionally replace or remove its image")
    @PutMapping("/{categoryId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateWithImage(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
        @RequestPart("category") request: UpdateProductCategoryRequest,
        @RequestPart("image", required = false) image: MultipartFile?,
        @RequestParam(defaultValue = "false") removeImage: Boolean,
    ): ResponseEntity<ProductCategoryResponse> = ResponseEntity.ok(
        merchantProductCategoryService.update(merchantId, categoryId, request, image?.toImageUpload(), removeImage)
    )

    private fun MultipartFile.toImageUpload(): ProductImageUpload {
        require(!isEmpty) { "Category image must not be empty" }
        return ProductImageUpload(contentType ?: throw IllegalArgumentException("Category image content type is required"), bytes)
    }

    @Operation(summary = "Delete a product category")
    @DeleteMapping("/{categoryId}")
    fun delete(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
    ): ResponseEntity<Void> {
        merchantProductCategoryService.delete(merchantId, categoryId)
        return ResponseEntity.noContent().build()
    }
}
