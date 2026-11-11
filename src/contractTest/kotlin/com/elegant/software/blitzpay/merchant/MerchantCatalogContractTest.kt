package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.persistence.repository.CatalogCategoryCount
import jakarta.persistence.Query
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.time.Instant
import java.util.Base64
import java.util.UUID

class MerchantCatalogContractTest : ContractVerifierBase() {
    private val merchantId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val branchId = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val categoryId = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val productId = UUID.fromString("44444444-4444-4444-8444-444444444441")
    private val base get() = "/v1/merchants/$merchantId/catalog"

    @BeforeEach
    fun setUpCatalog() {
        val session = mock<Session>()
        val filter = mock<Filter>()
        val query = mock<Query>()
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(filter)
        whenever(filter.setParameter(any<String>(), any())).thenReturn(filter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantBranchRepository.existsByMerchantIdAndIdAndActiveTrue(merchantId, branchId)).thenReturn(true)
        whenever(merchantBranchRepository.existsByMerchantIdAndId(merchantId, branchId)).thenReturn(true)
        whenever(merchantProductCategoryRepository.existsByIdAndMerchantId(categoryId, merchantId)).thenReturn(true)
        whenever(storageService.presignDownload(any(), any())).thenAnswer { "https://images.invalid/${it.getArgument<String>(0)}" }
    }

    @Test
    fun `GET catalog returns page metadata and separate list and detail images`() {
        whenever(merchantProductRepository.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(listOf(product(), product(UUID.fromString("44444444-4444-4444-8444-444444444442"))))

        webTestClient.get().uri("$base/products?branchId=$branchId&limit=1")
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].productId").isEqualTo(productId.toString())
            .jsonPath("$.items[0].imageUrl").isEqualTo("https://images.invalid/detail.jpg")
            .jsonPath("$.items[0].thumbnailUrl").isEqualTo("https://images.invalid/thumbnail.jpg")
            .jsonPath("$.limit").isEqualTo(1)
            .jsonPath("$.hasMore").isEqualTo(true)
            .jsonPath("$.nextCursor").isNotEmpty
    }

    @Test
    fun `GET empty page has terminal metadata and preserves filters and language`() {
        whenever(merchantProductRepository.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any())).thenReturn(emptyList())
        whenever(menuLanguageService.resolve(merchantId, null, "de")).thenReturn("de")
        webTestClient.get().uri("$base/products?branchId=$branchId&categoryId=$categoryId")
            .header("Accept-Language", "de")
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.items").isEmpty
            .jsonPath("$.hasMore").isEqualTo(false)
            .jsonPath("$.nextCursor").isEmpty
        verify(merchantProductRepository).findCatalogPage(eq(merchantId), eq(branchId), eq(categoryId), eq(false), eq(false), isNull(), isNull(), any())
    }

    @Test
    fun `GET categories returns only counted branch categories and uncategorized count`() {
        whenever(merchantProductRepository.catalogCategoryCounts(merchantId, branchId, false))
            .thenReturn(listOf(count(categoryId, 2), count(null, 1)))
        whenever(merchantProductCategoryRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(
            MerchantProductCategory(id = categoryId, merchantId = merchantId, name = "Drinks", imageStorageKey = "categories/drinks.png")
        ))
        whenever(storageService.presignDownload(eq("categories/drinks.png"), any())).thenReturn("https://images.invalid/category.png")
        webTestClient.get().uri("$base/categories?branchId=$branchId")
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.categories.length()").isEqualTo(1)
            .jsonPath("$.categories[0].name").isEqualTo("Drinks")
            .jsonPath("$.categories[0].imageUrl").isEqualTo("https://images.invalid/category.png")
            .jsonPath("$.categories[0].productCount").isEqualTo(2)
            .jsonPath("$.uncategorizedCount").isEqualTo(1)
            .jsonPath("$.totalCount").isEqualTo(3)
    }

    @Test
    fun `GET inactive catalog is denied to shoppers with a stable error code`() {
        for (endpoint in listOf("products", "categories")) {
            webTestClient.get().uri("$base/$endpoint?branchId=$branchId&includeInactive=true")
                .exchange().expectStatus().isForbidden
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("CATALOG_ACCESS_DENIED")
        }
    }

    @Test
    fun `GET branch staff may include inactive products only within the authorized branch`() {
        whenever(merchantProductRepository.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(listOf(product().apply { active = false }))
        webTestClient.get().uri("$base/products?branchId=$branchId&includeInactive=true")
            .headers { it.setBearerAuth(branchToken(branchId)) }
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.items[0].active").isEqualTo(false)
        webTestClient.get().uri("$base/products?branchId=$branchId&includeInactive=true")
            .headers { it.setBearerAuth(branchToken(UUID(0, 1))) }
            .exchange().expectStatus().isForbidden.expectBody()
            .jsonPath("$.code").isEqualTo("CATALOG_ACCESS_DENIED")
    }

    @Test
    fun `GET invalid cursor has a stable problem code`() {
        webTestClient.get().uri("$base/products?branchId=$branchId&cursor=invalid")
            .exchange().expectStatus().isBadRequest.expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_CATALOG_CURSOR")
    }

    @Test
    fun `GET validates page size conflicting filters and mandatory branch`() {
        for (query in listOf("branchId=$branchId&limit=0", "branchId=$branchId&limit=101",
            "branchId=$branchId&categoryId=$categoryId&uncategorized=true", "limit=24")) {
            webTestClient.get().uri("$base/products?$query").exchange().expectStatus().isBadRequest
        }
    }

    @Test
    fun `GET foreign or inactive shopper branch returns 404`() {
        whenever(merchantBranchRepository.existsByMerchantIdAndIdAndActiveTrue(merchantId, branchId)).thenReturn(false)
        webTestClient.get().uri("$base/products?branchId=$branchId").exchange().expectStatus().isNotFound
        webTestClient.get().uri("$base/categories?branchId=$branchId").exchange().expectStatus().isNotFound
    }

    @Test
    fun `GET catalog is anonymous for the storefront but inactive products are not`() {
        val anonymous = webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
        anonymous.post().uri("$base/thumbnails?branchId=$branchId").exchange().expectStatus().isUnauthorized
        anonymous.get().uri("$base/categories?branchId=$branchId&includeInactive=true")
            .exchange().expectStatus().isForbidden
    }

    @Test
    fun `POST thumbnail backfill is guarded and returns bounded batch metadata`() {
        webTestClient.post().uri("$base/thumbnails?branchId=$branchId")
            .exchange().expectStatus().isForbidden.expectBody()
            .jsonPath("$.code").isEqualTo("CATALOG_ACCESS_DENIED")
        whenever(merchantProductRepository.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(listOf(product()))
        webTestClient.post().uri("$base/thumbnails?branchId=$branchId")
            .headers { it.setBearerAuth(branchToken(branchId)) }
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.updated").isEqualTo(0)
            .jsonPath("$.skipped").isEqualTo(1)
            .jsonPath("$.hasMore").isEqualTo(false)
        webTestClient.post().uri("$base/thumbnails?branchId=$branchId&limit=25")
            .headers { it.setBearerAuth(branchToken(branchId)) }
            .exchange().expectStatus().isBadRequest
    }

    private fun product(id: UUID = productId) = MerchantProduct(
        id = id, merchantId = merchantId, merchantBranchId = branchId, name = "Coffee", unitPrice = BigDecimal.TEN,
        createdAt = Instant.parse("2026-11-10T08:00:00Z"), imageStorageKey = "detail.jpg", thumbnailStorageKey = "thumbnail.jpg",
    )

    private fun count(category: UUID?, total: Long) = object : CatalogCategoryCount {
        override val categoryId = category
        override val productCount = total
    }

    private fun branchToken(branch: UUID): String {
        val payload = """{"sub":"contract-test-user","groups":["/merchants/branch_$branch"]}"""
        return "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.signature"
    }
}
