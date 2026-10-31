package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.CatalogTranslationResponse
import com.elegant.software.blitzpay.merchant.api.MenuLanguageResponse
import com.elegant.software.blitzpay.merchant.api.TranslationCoverageResponse
import com.elegant.software.blitzpay.merchant.api.UpsertCatalogTranslationRequest
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductTranslation
import com.elegant.software.blitzpay.merchant.persistence.model.TranslationOrigin
import jakarta.persistence.Query
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CatalogTranslationContractTest : ContractVerifierBase() {

    private val session = mock<Session>()
    private val hibernateFilter = mock<Filter>()
    private val nativeQuery = mock<Query>()
    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    @BeforeEach
    fun setUpTenantFilter() {
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(hibernateFilter)
        whenever(hibernateFilter.setParameter(any<String>(), any())).thenReturn(hibernateFilter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(nativeQuery)
        whenever(nativeQuery.setParameter(any<String>(), any())).thenReturn(nativeQuery)
        whenever(nativeQuery.singleResult).thenReturn(merchantId.toString())
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
    }

    @Test
    fun `GET products serves the Accept-Language translation and names the returned language`() {
        val translated = product(productId, "Brezel")
        val untranslated = product(UUID.randomUUID(), "Leberkäse")
        whenever(merchantProductRepository.findAllByMerchantBranchId(branchId)).thenReturn(listOf(translated, untranslated))
        whenever(menuLanguageService.resolve(merchantId, null, "en-GB,en;q=0.9")).thenReturn("en")
        whenever(menuLanguageService.originalLanguage(merchantId)).thenReturn("de")
        whenever(catalogTranslationService.productTexts(any(), eq("en"))).thenReturn(
            mapOf(
                productId to MerchantProductTranslation(
                    productId = productId, language = "en", name = "Pretzel", description = "Freshly baked",
                    origin = TranslationOrigin.MACHINE, sourceHash = "hash",
                )
            )
        )

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/products?branchId=$branchId")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-GB,en;q=0.9")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].name").isEqualTo("Pretzel")
            .jsonPath("$[0].description").isEqualTo("Freshly baked")
            .jsonPath("$[0].language").isEqualTo("en")
            .jsonPath("$[1].name").isEqualTo("Leberkäse")
            .jsonPath("$[1].language").isEqualTo("de")
    }

    @Test
    fun `GET product passes the explicit language parameter to resolution`() {
        whenever(merchantProductRepository.findById(productId)).thenReturn(Optional.of(product(productId, "Brezel")))
        whenever(menuLanguageService.resolve(merchantId, "en", null)).thenReturn("en")

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/products/$productId?language=en")
            .exchange()
            .expectStatus().isOk

        verify(catalogTranslationService).productTexts(any(), eq("en"))
    }

    @Test
    fun `GET products with a language the merchant does not offer returns 400`() {
        whenever(menuLanguageService.resolve(eq(merchantId), eq("fr"), anyOrNull()))
            .thenThrow(IllegalArgumentException("Menu language 'fr' is not offered by merchant $merchantId"))

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/products?branchId=$branchId&language=fr")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Menu language 'fr' is not offered by merchant $merchantId")
    }

    @Test
    fun `GET merchant exposes original and supported menu languages`() {
        val merchant = Merchant(id = merchantId, merchantName = "Café Lena", businessType = "RESTAURANT", operatingCountry = "DE")
            .apply { originalMenuLanguage = "de" }
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant))
        whenever(menuLanguageService.supportedLanguages(merchantId)).thenReturn(sortedSetOf("en", "fr"))

        webTestClient.get()
            .uri("/v1/merchants/$merchantId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.originalMenuLanguage").isEqualTo("de")
            .jsonPath("$.supportedMenuLanguages[0]").isEqualTo("en")
            .jsonPath("$.supportedMenuLanguages[1]").isEqualTo("fr")
    }

    @Test
    fun `GET menu languages returns the catalogue`() {
        whenever(menuLanguageService.catalogue()).thenReturn(
            listOf(MenuLanguageResponse("de", "Deutsch"), MenuLanguageResponse("en", "English"))
        )

        webTestClient.get()
            .uri("/v1/merchants/menu-languages")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[1].code").isEqualTo("en")
            .jsonPath("$[1].displayName").isEqualTo("English")
    }

    @Test
    fun `GET product translations returns origin and staleness`() {
        whenever(catalogTranslationService.productTranslations(merchantId, productId)).thenReturn(
            listOf(translationResponse(origin = TranslationOrigin.MERCHANT, stale = true))
        )

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/products/$productId/translations")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].language").isEqualTo("en")
            .jsonPath("$[0].name").isEqualTo("Pretzel")
            .jsonPath("$[0].origin").isEqualTo("MERCHANT")
            .jsonPath("$[0].stale").isEqualTo(true)
    }

    @Test
    fun `PUT product translation returns 200`() {
        whenever(
            catalogTranslationService.putProductTranslation(
                merchantId, productId, "en", UpsertCatalogTranslationRequest("Pretzel", "Freshly baked")
            )
        ).thenReturn(translationResponse(origin = TranslationOrigin.MERCHANT))

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/products/$productId/translations/en")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Pretzel","description":"Freshly baked"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.origin").isEqualTo("MERCHANT")
            .jsonPath("$.stale").isEqualTo(false)
    }

    @Test
    fun `PUT product translation for an unsupported language returns 400`() {
        whenever(catalogTranslationService.putProductTranslation(eq(merchantId), eq(productId), eq("fr"), any()))
            .thenThrow(IllegalArgumentException("Menu language 'fr' is not a supported menu language"))

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/products/$productId/translations/fr")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Bretzel"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Menu language 'fr' is not a supported menu language")
    }

    @Test
    fun `DELETE product translation returns 204`() {
        webTestClient.delete()
            .uri("/v1/merchants/$merchantId/products/$productId/translations/en")
            .exchange()
            .expectStatus().isNoContent

        verify(catalogTranslationService).deleteProductTranslation(merchantId, productId, "en")
    }

    @Test
    fun `DELETE missing translation returns 404`() {
        whenever(catalogTranslationService.deleteProductTranslation(merchantId, productId, "en"))
            .thenThrow(NoSuchElementException("Translation not found"))

        webTestClient.delete()
            .uri("/v1/merchants/$merchantId/products/$productId/translations/en")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `PUT category translation returns 200`() {
        val categoryId = UUID.randomUUID()
        whenever(catalogTranslationService.putCategoryTranslation(eq(merchantId), eq(categoryId), eq("en"), any()))
            .thenReturn(translationResponse(name = "Main dishes", description = null))

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/product-categories/$categoryId/translations/en")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Main dishes"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Main dishes")
    }

    @Test
    fun `GET category list passes Accept-Language to resolution`() {
        whenever(menuLanguageService.resolve(merchantId, null, "en")).thenReturn("en")

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/product-categories")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
            .exchange()
            .expectStatus().isOk

        verify(merchantProductCategoryService).list(merchantId, "en")
    }

    @Test
    fun `GET translation coverage returns counts per language`() {
        whenever(catalogTranslationService.coverage(merchantId)).thenReturn(
            listOf(TranslationCoverageResponse("en", productsTotal = 200, productsTranslated = 180, categoriesTotal = 8, categoriesTranslated = 8))
        )

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/translation-coverage")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].language").isEqualTo("en")
            .jsonPath("$[0].productsTotal").isEqualTo(200)
            .jsonPath("$[0].productsTranslated").isEqualTo(180)
            .jsonPath("$[0].categoriesTranslated").isEqualTo(8)
    }

    private fun product(id: UUID, name: String) = MerchantProduct(
        id = id,
        merchantId = merchantId,
        merchantBranchId = branchId,
        name = name,
        unitPrice = BigDecimal("3.20"),
    )

    private fun translationResponse(
        name: String = "Pretzel",
        description: String? = "Freshly baked",
        origin: TranslationOrigin = TranslationOrigin.MACHINE,
        stale: Boolean = false,
    ) = CatalogTranslationResponse(
        language = "en",
        name = name,
        description = description,
        origin = origin,
        stale = stale,
        updatedAt = Instant.parse("2026-10-31T12:00:00Z"),
    )
}
