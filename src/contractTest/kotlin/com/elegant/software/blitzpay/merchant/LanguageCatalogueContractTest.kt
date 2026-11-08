package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.MenuLanguageResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders

class LanguageCatalogueContractTest : ContractVerifierBase() {
    @Test
    fun `authenticated clients share language metadata and independent capabilities`() {
        whenever(menuLanguageService.catalogue()).thenReturn(listOf(
            MenuLanguageResponse("en", "English", "English", "GB", "ltr", true, true),
            MenuLanguageResponse("de", "German", "Deutsch", "DE", "ltr", true, true),
            MenuLanguageResponse("ar", "Arabic", "العربية", "SA", "rtl", true, true),
            MenuLanguageResponse("fr", "French", "Français", null, "ltr", false, true),
        ))
        val expected = requireNotNull(javaClass.getResourceAsStream("/testdata/merchant/language-catalogue.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        webTestClient.get().uri("/v1/merchants/menu-languages").exchange()
            .expectStatus().isOk.expectBody().json(expected)
    }

    @Test
    fun `shared catalogue remains authenticated and duplicate endpoint is removed`() {
        val anonymous = webTestClient.mutate()
            .defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()
        anonymous.get().uri("/v1/merchants/menu-languages").exchange().expectStatus().isUnauthorized
        anonymous.post().uri("/v1/merchants/menu-languages").exchange().expectStatus().isUnauthorized
        webTestClient.get().uri("/v1/admin/ui-languages").exchange().expectStatus().isNotFound
    }
}
