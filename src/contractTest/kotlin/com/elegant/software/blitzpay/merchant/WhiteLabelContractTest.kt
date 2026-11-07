package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.*
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantWhiteLabel
import com.elegant.software.blitzpay.merchant.support.MerchantTestFixtureLoader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import java.util.Base64
import java.util.Optional

class WhiteLabelContractTest : ContractVerifierBase() {
    private val merchant = MerchantTestFixtureLoader.merchant()
    private val base get() = "/v1/merchants/${merchant.id}/white-label"
    private val admin = "Bearer fakeheader." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"sub":"admin","realm_access":{"roles":["blitzpay-admin"]}}""".toByteArray()) + ".fakesig"
    private val settings get() = requireNotNull(javaClass.getResource("/testdata/merchant/white-label.json")).readText()

    @BeforeEach
    fun setupProfile() {
        whenever(merchantRepository.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(merchantWhiteLabelRepository.saveAndFlush(any<MerchantWhiteLabel>())).thenAnswer {
            val profile = it.arguments[0] as MerchantWhiteLabel
            whenever(merchantWhiteLabelRepository.findByMerchantIdAndBranchIdAndApp(profile.merchantId, profile.branchId, profile.app)).thenReturn(profile)
            profile
        }
    }

    @Test
    fun `public storefront sees resolved settings but cannot read editor or publish`() {
        webTestClient.mutate().defaultHeaders { it.clear() }.build().get().uri("$base/effective?app=STOREFRONT").exchange()
            .expectStatus().isOk.expectHeader().valueEquals("Cache-Control", "no-store")
            .expectBody().jsonPath("$.light.surface").isEqualTo("#F2F2F7").jsonPath("$.settings").doesNotExist()
        webTestClient.mutate().defaultHeaders { it.clear() }.build().get().uri("$base?app=STOREFRONT").exchange().expectStatus().isUnauthorized
        webTestClient.get().uri("$base?app=STOREFRONT").exchange().expectStatus().isForbidden
        webTestClient.put().uri("$base?app=STOREFRONT").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"settings":$settings,"revision":-1}""").exchange().expectStatus().isForbidden
    }

    @Test
    fun `admin publishes an app configuration and stale revision gets conflict`() {
        val payload = """{"settings":$settings,"revision":-1}"""
        webTestClient.put().uri("$base?app=KITCHEN").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload).exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.effective.displayName").isEqualTo("Studio North").jsonPath("$.effective.dark.card").isEqualTo("#242426")
        webTestClient.put().uri("$base?app=KITCHEN").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload).exchange().expectStatus().isEqualTo(409)
        webTestClient.get().uri("$base/effective?app=MERCHANT").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
    }

    @Test
    fun `invalid colors return role specific validation detail`() {
        webTestClient.put().uri("$base?app=STOREFRONT").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"settings":${settings.replace("\"background\": \"#FFFFFF\"", "\"background\": \"red\"")},"revision":-1}""")
            .exchange().expectStatus().isBadRequest.expectBody().jsonPath("$.detail").value<String> {
                kotlin.test.assertTrue(it.contains("light.background"))
            }
    }

    @Test
    fun `merchant manager may manage own app settings and themes but not another merchant`() {
        val userId = java.util.UUID.randomUUID()
        val manager = "Bearer fakeheader." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"$userId"}""".toByteArray()) + ".fakesig"
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(userId)).thenReturn(listOf(
            com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment(keycloakUserId = userId,
                merchantId = merchant.id, role = com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole.MERCHANT_MANAGER)))
        webTestClient.get().uri("$base?app=MERCHANT").header("Authorization", manager).exchange().expectStatus().isOk
        webTestClient.put().uri("$base?app=MERCHANT").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"settings":$settings,"revision":-1}""").exchange().expectStatus().isOk
        webTestClient.get().uri("/v1/merchants/${java.util.UUID.randomUUID()}/white-label?app=MERCHANT")
            .header("Authorization", manager).exchange().expectStatus().isForbidden
        whenever(merchantRepository.existsById(merchant.id)).thenReturn(true)
        webTestClient.get().uri("/v1/merchants/${merchant.id}/themes?app=MERCHANT")
            .header("Authorization", manager).exchange().expectStatus().isOk
    }

    @Test
    fun `branch editor publishes overrides and public storefront reads resolved branch identity`() {
        val branch = com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch(merchantId = merchant.id, name = "Second location")
        whenever(merchantBranchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        val branchBase = "/v1/merchants/${merchant.id}/branches/${branch.id}/white-label"
        webTestClient.put().uri("$base?app=STOREFRONT").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"settings":$settings,"revision":-1}""").exchange().expectStatus().isOk
        webTestClient.put().uri("$branchBase?app=STOREFRONT").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"settings":${settings.replace("Book at Studio North", "Second location")},"revision":-1}""")
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.effective.title").isEqualTo("Second location")
        webTestClient.get().uri("$branchBase?app=STOREFRONT").header("Authorization", admin)
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.inheritedConfiguration.title").isEqualTo("Book at Studio North")
        webTestClient.mutate().defaultHeaders { it.clear() }.build().get().uri("$branchBase/effective?app=STOREFRONT")
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.title").isEqualTo("Second location")
    }

    @Test
    fun `logo upload routes validate content type scope and editor access`() {
        val branch = com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch(merchantId = merchant.id, name = "Second location")
        whenever(merchantBranchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(storageService.presignUpload(any(), any(), any())).thenAnswer {
            com.elegant.software.blitzpay.storage.PresignedUpload(it.arguments[0] as String,
                "https://upload.example", java.time.Instant.parse("2026-11-07T00:00:00Z"))
        }
        listOf(base, "/v1/merchants/${merchant.id}/branches/${branch.id}/white-label").forEach { scope ->
            webTestClient.post().uri("$scope/logo/upload-url").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"contentType":"image/png"}""")
                .exchange().expectStatus().isOk.expectBody().jsonPath("$.uploadUrl").isEqualTo("https://upload.example")
            webTestClient.post().uri("$scope/logo/upload-url").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"contentType":"image/svg+xml"}""")
                .exchange().expectStatus().isBadRequest
            webTestClient.post().uri("$scope/logo/upload-url").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"contentType":"image/png"}""").exchange().expectStatus().isForbidden
        }
    }
}
