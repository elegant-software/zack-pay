package com.elegant.software.blitzpay.merchant

import org.springframework.http.HttpStatus
import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryRequest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import com.elegant.software.blitzpay.merchant.service.ProductImageUpload
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID

class MerchantProductCategoryContractTest : ContractVerifierBase() {

    @Test
    fun `multipart POST accepts category JSON and image and returns its URL`() {
        val merchantId = UUID.randomUUID()
        val image = ProductImageUpload("image/png", ByteArray(2 * 1024 * 1024) { 1 })
        whenever(merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>(), eq(image)))
            .thenReturn(categoryResponse(name = "Drinks").copy(imageUrl = "https://images.example.test/category.png"))
        val body = MultipartBodyBuilder().apply {
            part("category", """{"name":"Drinks"}""").contentType(MediaType.APPLICATION_JSON)
            part("image", image.bytes).filename("drinks.png").contentType(MediaType.IMAGE_PNG)
        }
        webTestClient.post().uri("/v1/merchants/$merchantId/product-categories")
            .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue(body.build()).exchange()
            .expectStatus().isCreated.expectBody()
            .jsonPath("$.imageUrl").isEqualTo("https://images.example.test/category.png")
        verify(merchantProductCategoryService).create(merchantId, CreateProductCategoryRequest("Drinks"), image)
    }

    @Test
    fun `multipart PUT explicitly removes a category image`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.update(eq(merchantId), eq(categoryId), any<UpdateProductCategoryRequest>(), isNull(), eq(true)))
            .thenReturn(categoryResponse(id = categoryId, name = "Drinks"))
        val body = MultipartBodyBuilder().apply {
            part("category", """{"name":"Drinks"}""").contentType(MediaType.APPLICATION_JSON)
            part("removeImage", "true")
        }
        webTestClient.put().uri("/v1/merchants/$merchantId/product-categories/$categoryId")
            .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue(body.build()).exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.imageUrl").doesNotExist()
        verify(merchantProductCategoryService).update(merchantId, categoryId, UpdateProductCategoryRequest("Drinks"), null, true)
    }

    @Test
    fun `multipart PUT replaces a category image`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val image = ProductImageUpload("image/png", byteArrayOf(4, 5, 6))
        whenever(merchantProductCategoryService.update(eq(merchantId), eq(categoryId), any<UpdateProductCategoryRequest>(), eq(image), eq(false)))
            .thenReturn(categoryResponse(id = categoryId, name = "Drinks").copy(imageUrl = "https://images.example.test/new.png"))
        val body = MultipartBodyBuilder().apply {
            part("category", """{"name":"Drinks"}""").contentType(MediaType.APPLICATION_JSON)
            part("image", image.bytes).filename("drinks.png").contentType(MediaType.IMAGE_PNG)
        }
        webTestClient.put().uri("/v1/merchants/$merchantId/product-categories/$categoryId")
            .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue(body.build()).exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.imageUrl").isEqualTo("https://images.example.test/new.png")
    }

    @Test
    fun `multipart POST rejects unsupported or empty image`() {
        val merchantId = UUID.randomUUID()
        for ((type, bytes) in listOf(MediaType.TEXT_PLAIN to byteArrayOf(1), MediaType.IMAGE_PNG to byteArrayOf())) {
            val body = MultipartBodyBuilder().apply {
                part("category", """{"name":"Drinks"}""").contentType(MediaType.APPLICATION_JSON)
                part("image", bytes).filename("image").contentType(type)
            }
            webTestClient.post().uri("/v1/merchants/$merchantId/product-categories")
                .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue(body.build()).exchange()
                .expectStatus().isBadRequest
        }
    }

    @Test
    fun `anonymous category image upload is unauthorized`() {
        val body = MultipartBodyBuilder().apply {
            part("category", """{"name":"Drinks"}""").contentType(MediaType.APPLICATION_JSON)
            part("image", byteArrayOf(1)).filename("drinks.png").contentType(MediaType.IMAGE_PNG)
        }
        webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
            .post().uri("/v1/merchants/${UUID.randomUUID()}/product-categories")
            .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue(body.build()).exchange()
            .expectStatus().isUnauthorized
    }

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
            merchantProductCategoryService.update(
                eq(merchantId),
                eq(categoryId),
                any<UpdateProductCategoryRequest>()
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
    fun `PUT product category with duration returns 200`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(
            merchantProductCategoryService.update(
                eq(merchantId),
                eq(categoryId),
                any<UpdateProductCategoryRequest>()
            )
        ).thenReturn(categoryResponse(id = categoryId, name = "Haircut", estimatedDurationMinutes = 30))

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Haircut","estimatedDurationMinutes":30}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.estimatedDurationMinutes").isEqualTo(30)
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
    fun `POST duplicate category returns 409`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>()))
            .thenThrow(IllegalArgumentException("A category named 'drinks' already exists for this merchant"))

        webTestClient.post()
            .uri("/v1/merchants/$merchantId/product-categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"drinks"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.detail").isEqualTo("A category named 'drinks' already exists for this merchant")
    }

    @Test
    fun `DELETE category with assigned products returns 409`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.delete(merchantId, categoryId))
            .thenThrow(ApiException(HttpStatus.CONFLICT, "Cannot delete category 'Drinks': 5 product(s) are still assigned to it"))

        webTestClient.delete()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Cannot delete category 'Drinks': 5 product(s) are still assigned to it")
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
            .jsonPath("$.detail").isEqualTo("Category not found: $categoryId")
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
