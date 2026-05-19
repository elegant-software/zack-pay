package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.api.RenameProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryDurationRequest
import com.elegant.software.blitzpay.merchant.application.MerchantProductCategoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

data class ProductCategoryErrorResponse(val error: String)

@Tag(name = "Merchant Product Categories", description = "Manage merchant product categories")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/product-categories", version = "1")
class MerchantProductCategoryController(
    private val merchantProductCategoryService: MerchantProductCategoryService,
) {
    @Operation(summary = "Create a product category")
    @PostMapping
    fun create(
        @PathVariable merchantId: UUID,
        @RequestBody request: CreateProductCategoryRequest,
    ): Mono<ResponseEntity<ProductCategoryResponse>> =
        Mono.fromCallable { merchantProductCategoryService.create(merchantId, request) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }

    @Operation(summary = "List all product categories")
    @GetMapping
    fun list(@PathVariable merchantId: UUID): Mono<ResponseEntity<List<ProductCategoryResponse>>> =
        Mono.fromCallable { merchantProductCategoryService.list(merchantId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Rename a product category")
    @PutMapping("/{categoryId}")
    fun rename(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
        @RequestBody request: RenameProductCategoryRequest,
    ): Mono<ResponseEntity<ProductCategoryResponse>> =
        Mono.fromCallable { merchantProductCategoryService.rename(merchantId, categoryId, request) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Update estimated service duration for a product category")
    @PatchMapping("/{categoryId}/duration")
    fun updateDuration(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
        @RequestBody request: UpdateProductCategoryDurationRequest,
    ): Mono<ResponseEntity<ProductCategoryResponse>> =
        Mono.fromCallable { merchantProductCategoryService.updateDuration(merchantId, categoryId, request) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @Operation(summary = "Delete a product category")
    @DeleteMapping("/{categoryId}")
    fun delete(
        @PathVariable merchantId: UUID,
        @PathVariable categoryId: UUID,
    ): Mono<ResponseEntity<Void>> =
        Mono.fromCallable { merchantProductCategoryService.delete(merchantId, categoryId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.noContent().build<Void>() }

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(ex: IllegalArgumentException): ResponseEntity<ProductCategoryErrorResponse> =
        ResponseEntity.badRequest().body(ProductCategoryErrorResponse(ex.message ?: "Invalid category request"))

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(ex: NoSuchElementException): ResponseEntity<ProductCategoryErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProductCategoryErrorResponse(ex.message ?: "Category not found"))

    @ExceptionHandler(IllegalStateException::class)
    fun conflict(ex: IllegalStateException): ResponseEntity<ProductCategoryErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ProductCategoryErrorResponse(ex.message ?: "Category conflict"))
}
