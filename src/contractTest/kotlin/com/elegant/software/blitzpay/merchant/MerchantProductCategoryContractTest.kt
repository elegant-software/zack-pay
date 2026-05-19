package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.api.RenameProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryDurationRequest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import java.time.Instant
import java.util.UUID

class MerchantProductCategoryContractTest : ContractVerifierBase() {

    @Test
    fun `POST product categories returns 201`() {
        val merchantId = UUID.randomUUID()
        val response = categoryResponse(name = "Drinks")
        whenever(merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>()))
            .thenReturn(response)

        webTestClient.post()
            .uri("/v1/merchants/$merchantId/product-categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Drinks"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isEqualTo(response.id.toString())
            .jsonPath("$.name").isEqualTo("Drinks")
    }

    @Test
    fun `GET product categories returns 200`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantProductCategoryService.list(merchantId)).thenReturn(
            listOf(categoryResponse(name = "Bakery"), categoryResponse(name = "Drinks"))
        )

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/product-categories")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].name").isEqualTo("Bakery")
    }

    @Test
    fun `PUT product categories returns 200`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(
            merchantProductCategoryService.rename(
                eq(merchantId),
                eq(categoryId),
                any<RenameProductCategoryRequest>()
            )
        ).thenReturn(categoryResponse(id = categoryId, name = "Soft Drinks"))

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Soft Drinks"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Soft Drinks")
    }

    @Test
    fun `PATCH product category duration returns 200`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(
            merchantProductCategoryService.updateDuration(
                eq(merchantId),
                eq(categoryId),
                any<UpdateProductCategoryDurationRequest>()
            )
        ).thenReturn(categoryResponse(id = categoryId, name = "Haircut", estimatedDurationMinutes = 30))

        webTestClient.patch()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId/duration")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"estimatedDurationMinutes":30}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.estimatedDurationMinutes").isEqualTo(30)
    }

    @Test
    fun `PATCH product category duration with null clears duration`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(
            merchantProductCategoryService.updateDuration(
                eq(merchantId),
                eq(categoryId),
                any<UpdateProductCategoryDurationRequest>()
            )
        ).thenReturn(categoryResponse(id = categoryId, name = "Haircut", estimatedDurationMinutes = null))

        webTestClient.patch()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId/duration")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"estimatedDurationMinutes":null}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.estimatedDurationMinutes").doesNotExist()
    }

    @Test
    fun `DELETE product categories returns 204`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()

        webTestClient.delete()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `POST duplicate category returns 400`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>()))
            .thenThrow(IllegalArgumentException("A category named 'drinks' already exists for this merchant"))

        webTestClient.post()
            .uri("/v1/merchants/$merchantId/product-categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"drinks"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("A category named 'drinks' already exists for this merchant")
    }

    @Test
    fun `DELETE category with assigned products returns 409`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.delete(merchantId, categoryId))
            .thenThrow(IllegalStateException("Cannot delete category 'Drinks': 5 product(s) are still assigned to it"))

        webTestClient.delete()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Cannot delete category 'Drinks': 5 product(s) are still assigned to it")
    }

    @Test
    fun `DELETE missing category returns 404`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.delete(merchantId, categoryId))
            .thenThrow(NoSuchElementException("Category not found: $categoryId"))

        webTestClient.delete()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("Category not found: $categoryId")
    }

    private fun categoryResponse(
        id: UUID = UUID.randomUUID(),
        name: String,
        estimatedDurationMinutes: Int? = null
    ) = ProductCategoryResponse(
        id = id,
        name = name,
        estimatedDurationMinutes = estimatedDurationMinutes,
        createdAt = Instant.parse("2026-04-29T10:00:00Z"),
        updatedAt = Instant.parse("2026-04-29T10:00:00Z")
    )
}
