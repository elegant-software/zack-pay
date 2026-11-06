package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.storage.PresignedUpload
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.Base64
import java.util.Optional
import java.util.UUID
import java.time.Instant

class MerchantBrandingContractTest : ContractVerifierBase() {
    private val merchant = Merchant(merchantName = "Example", businessType = "RETAIL", operatingCountry = "DE")
    private val branch = MerchantBranch(merchantId = merchant.id, name = "Branch")
    private val base get() = "/v1/merchants/${merchant.id}"
    private val admin = "Bearer fakeheader." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"sub":"admin","realm_access":{"roles":["blitzpay-admin"]}}""".toByteArray()) + ".fakesig"

    @BeforeEach
    fun setupBranding() {
        whenever(merchantRepository.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(merchantBranchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
    }

    @Test
    fun `authenticated shopper can read default merchant and branch branding`() {
        listOf("$base/branding", "$base/branches/${branch.id}/branding").forEach { path ->
            webTestClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer $fakeJwt")
                .exchange().expectStatus().isOk.expectBody()
                .jsonPath("$.effective.platformName").isEqualTo("Simpleio")
                .jsonPath("$.effective.enabled").isEqualTo(false)
                .jsonPath("$.effective.displayName").isEqualTo("Example")
        }
    }

    @Test
    fun `only realm admins can save merchant and branch branding`() {
        listOf("$base/branding", "$base/branches/${branch.id}/branding").forEach { path ->
            webTestClient.put().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer $fakeJwt")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"enabled":true}""")
                .exchange().expectStatus().isForbidden
            webTestClient.put().uri(path).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"enabled":true,"primaryColor":"#000000"}""")
                .exchange().expectStatus().isOk.expectBody()
                .jsonPath("$.settings.primaryColor").isEqualTo("#000000")
        }
    }

    @Test
    fun `non admins cannot create branding uploads in either scope`() {
        listOf("$base/branding", "$base/branches/${branch.id}/branding").forEach { path ->
            webTestClient.post().uri("$path/logo/upload-url").header(HttpHeaders.AUTHORIZATION, "Bearer $fakeJwt")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"contentType":"image/png"}""")
                .exchange().expectStatus().isForbidden
        }
    }

    @Test
    fun `invalid colors and cross merchant branches are rejected`() {
        webTestClient.put().uri("$base/branding").header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"primaryColor":"red"}""")
            .exchange().expectStatus().isBadRequest.expectBody()
            .jsonPath("$.errors.primaryColor").exists()
        val alien = MerchantBranch(merchantId = UUID.randomUUID(), name = "Alien")
        whenever(merchantBranchRepository.findById(alien.id)).thenReturn(Optional.of(alien))
        webTestClient.get().uri("$base/branches/${alien.id}/branding")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $fakeJwt").exchange().expectStatus().isNotFound
    }

    @Test
    fun `anonymous branding access requires authentication`() {
        webTestClient.mutate().defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()
            .get().uri("$base/branding").exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `admins can upload isolated branding logos in both scopes`() {
        whenever(storageService.presignUpload(any(), any(), any())).thenAnswer {
            PresignedUpload(it.arguments[0] as String, "https://upload.example", Instant.parse("2026-11-06T12:00:00Z"))
        }
        listOf("$base/branding", "$base/branches/${branch.id}/branding").forEach { path ->
            webTestClient.post().uri("$path/logo/upload-url").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"contentType":"image/png"}""")
                .exchange().expectStatus().isOk.expectBody()
                .jsonPath("$.uploadUrl").isEqualTo("https://upload.example")
                .jsonPath("$.storageKey").exists()
        }
    }

    @Test
    fun `saved branch values inherit merchant fields and can be reset`() {
        webTestClient.put().uri("$base/branding").header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"enabled":true,"displayName":"Parent Brand","primaryColor":"#123456"}""")
            .exchange().expectStatus().isOk
        val path = "$base/branches/${branch.id}/branding"
        webTestClient.put().uri(path).header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"displayName":"Branch Brand"}""")
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.effective.displayName").isEqualTo("Branch Brand")
            .jsonPath("$.effective.primaryColor").isEqualTo("#123456")
        webTestClient.put().uri(path).header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.effective.displayName").isEqualTo("Parent Brand")
    }
}
