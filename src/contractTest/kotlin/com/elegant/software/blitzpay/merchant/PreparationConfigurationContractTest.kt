package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantPreparationConfigurationRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Base64
import java.util.Optional
import java.util.UUID

class PreparationConfigurationContractTest : ContractVerifierBase() {
    private val configurations get() = preparationConfigurationRepository
    private val merchantId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val branchId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val base = "/v1/merchants/$merchantId/branches/$branchId/preparation-configuration"
    private val admin = "Bearer fakeheader." + Base64.getUrlEncoder().withoutPadding().encodeToString(
        """{"sub":"admin","realm_access":{"roles":["blitzpay-admin"]}}""".toByteArray()
    ) + ".fakesig"

    @BeforeEach
    fun branch() {
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(Optional.of(
            MerchantBranch(id = branchId, merchantId = merchantId, name = "Bona-me")
        ))
        whenever(configurations.findById(branchId)).thenReturn(Optional.empty())
        whenever(configurations.save(any<com.elegant.software.blitzpay.merchant.persistence.model.MerchantPreparationConfiguration>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `legacy branches return empty configuration and admin can configure food and drinks`() {
        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, admin).exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.stations.length()").isEqualTo(0)
        webTestClient.put().uri(base).header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"stations":[{"id":"44444444-4444-4444-4444-444444444441","name":"Food Kitchen"},{"id":"44444444-4444-4444-4444-444444444442","name":"Drinks Bar"}],"categoryDefaults":{},"productOverrides":{}}""")
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.stations[0].name").isEqualTo("Food Kitchen")
            .jsonPath("$.stations[1].name").isEqualTo("Drinks Bar")
    }

    @Test
    fun `unauthorized callers cannot change stations`() {
        webTestClient.put().uri(base).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"stations":[],"categoryDefaults":{},"productOverrides":{}}""")
            .exchange().expectStatus().isForbidden
    }
    @Test
    fun `routing cannot reference a station outside the branch configuration`() {
        webTestClient.put().uri(base).header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"stations":[],"categoryDefaults":{"66666666-6666-6666-6666-666666666666":"44444444-4444-4444-4444-444444444441"}}""")
            .exchange().expectStatus().isBadRequest
    }

    @Test
    fun `station names are trimmed and duplicate names rejected`() {
        webTestClient.put().uri(base).header(HttpHeaders.AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"stations":[{"id":"44444444-4444-4444-4444-444444444441","name":"Food Kitchen"},{"id":"44444444-4444-4444-4444-444444444442","name":" food kitchen "}]}""")
            .exchange().expectStatus().isBadRequest
    }

    @Test
    fun `a branch belonging to another merchant cannot be configured`() {
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(Optional.of(
            MerchantBranch(id = branchId, merchantId = UUID.fromString("99999999-9999-9999-9999-999999999999"), name = "Other")
        ))
        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, admin)
            .exchange().expectStatus().isNotFound
    }
}
