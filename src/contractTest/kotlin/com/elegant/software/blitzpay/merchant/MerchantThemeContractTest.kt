package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.ThemeApp
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTheme
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.Base64
import java.util.UUID

class MerchantThemeContractTest : ContractVerifierBase() {
    private val merchantId = UUID.randomUUID()
    private val base get() = "/v1/merchants/$merchantId/themes"
    private val admin = "Bearer fakeheader." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"sub":"admin","realm_access":{"roles":["blitzpay-admin"]}}""".toByteArray()) + ".fakesig"
    private val payload get() = requireNotNull(javaClass.getResource("/testdata/merchant/theme.json")).readText()

    @BeforeEach
    fun setupThemes() {
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantThemeRepository.save(any<MerchantTheme>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `admin can create list and update merchant themes`() {
        val theme = MerchantTheme(merchantId = merchantId, name = "Summer", primaryColor = "#123ABC", accentColor = "#FFCC00")
        whenever(merchantThemeRepository.findAllByMerchantIdAndAppOrderByNameAscIdAsc(merchantId, ThemeApp.SHOPPER)).thenReturn(listOf(theme))
        whenever(merchantThemeRepository.findByIdAndMerchantIdAndApp(theme.id, merchantId, ThemeApp.SHOPPER)).thenReturn(theme)
        webTestClient.post().uri(base).header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload).exchange()
            .expectStatus().isCreated.expectBody().jsonPath("$.name").isEqualTo("Summer")
            .jsonPath("$.primaryColor").isEqualTo("#123ABC")
            .jsonPath("$.primaryTextColor").isEqualTo("#FFFFFF")
        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, admin).exchange()
            .expectStatus().isOk.expectBody().jsonPath("$[0].id").isEqualTo(theme.id.toString())
        webTestClient.put().uri("$base/${theme.id}").header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload.replace("Summer", "Winter"))
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.name").isEqualTo("Winter")
    }

    @Test
    fun `merchant app themes can be managed without exposing them in shopper scope`() {
        val theme = MerchantTheme(merchantId = merchantId, name = "Summer", primaryColor = "#123ABC",
            accentColor = "#FFCC00", app = ThemeApp.MERCHANT)
        whenever(merchantThemeRepository.findAllByMerchantIdAndAppOrderByNameAscIdAsc(merchantId, ThemeApp.MERCHANT)).thenReturn(listOf(theme))
        whenever(merchantThemeRepository.findAllByMerchantIdAndAppOrderByNameAscIdAsc(merchantId, ThemeApp.SHOPPER)).thenReturn(emptyList())
        whenever(merchantThemeRepository.findByIdAndMerchantIdAndApp(theme.id, merchantId, ThemeApp.MERCHANT)).thenReturn(theme)
        webTestClient.post().uri("$base?app=MERCHANT").header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload).exchange()
            .expectStatus().isCreated.expectBody().jsonPath("$.app").isEqualTo("MERCHANT")
        webTestClient.get().uri("$base?app=MERCHANT").header(HttpHeaders.AUTHORIZATION, admin).exchange()
            .expectStatus().isOk.expectBody().jsonPath("$[0].app").isEqualTo("MERCHANT")
        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, admin).exchange()
            .expectStatus().isOk.expectBody().json("[]")
        webTestClient.put().uri("$base/${theme.id}?app=MERCHANT").header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload.replace("Summer", "Winter"))
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.name").isEqualTo("Winter")
        webTestClient.put().uri("$base/${theme.id}").header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload)
            .exchange().expectStatus().isNotFound
        webTestClient.get().uri("$base?app=UNKNOWN").header(HttpHeaders.AUTHORIZATION, admin)
            .exchange().expectStatus().isBadRequest
        webTestClient.post().uri("$base?app=MERCHANT").header(HttpHeaders.AUTHORIZATION, "Bearer $fakeJwt")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload).exchange().expectStatus().isForbidden
    }

    @Test
    fun `merchant users cannot manage theme library`() {
        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, "Bearer $fakeJwt")
            .exchange().expectStatus().isForbidden
        webTestClient.post().uri(base).header(HttpHeaders.AUTHORIZATION, "Bearer $fakeJwt")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload).exchange().expectStatus().isForbidden
        webTestClient.put().uri("$base/${UUID.randomUUID()}").header(HttpHeaders.AUTHORIZATION, "Bearer $fakeJwt")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload).exchange().expectStatus().isForbidden
        verifyNoInteractions(merchantThemeRepository)
    }

    @Test
    fun `invalid colors and themes outside merchant scope are rejected`() {
        webTestClient.post().uri(base).header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload.replace("#123abc", "red"))
            .exchange().expectStatus().isBadRequest
        webTestClient.put().uri("$base/${UUID.randomUUID()}").header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(payload).exchange().expectStatus().isNotFound
    }
}
