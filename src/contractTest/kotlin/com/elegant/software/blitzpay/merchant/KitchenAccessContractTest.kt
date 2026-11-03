package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.iam.web.KitchenAccessStatusResponse
import com.elegant.software.blitzpay.merchant.iam.web.KitchenLoginResponse
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import java.util.Base64
import java.util.Optional
import java.util.UUID

class KitchenAccessContractTest : ContractVerifierBase() {

    private val merchantId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val managerId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val employeeId = UUID.fromString("66666666-6666-6666-6666-666666666666")
    private val branch = MerchantBranch(
        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        merchantId = merchantId,
        name = "Altstadt",
        branchCode = "BR-1A2B3C4D5E6F",
    )
    private val base = "/v1/merchant/branches/${branch.id}/kitchen"

    private fun bearer(sub: UUID): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$sub"}""".toByteArray())
        return "Bearer fakeheader.$payload.fakesig"
    }

    private val manager = bearer(managerId)
    private val employee = bearer(employeeId)

    @BeforeEach
    fun givenBranchAndAssignments() {
        whenever(merchantBranchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(managerId)).thenReturn(
            listOf(MerchantUserAssignment(keycloakUserId = managerId, merchantId = merchantId, role = MerchantOperationsRole.MERCHANT_MANAGER))
        )
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(employeeId)).thenReturn(
            listOf(
                MerchantUserAssignment(
                    keycloakUserId = employeeId, merchantId = merchantId, branchId = branch.id,
                    role = MerchantOperationsRole.MERCHANT_EMPLOYEE,
                )
            )
        )
    }

    @Test
    fun `enable returns the kitchen login once with no-store`() {
        whenever(kitchenAccessService.enable(any())).thenReturn(
            KitchenLoginResponse(
                username = "kitchen-br-1a2b3c4d5e6f",
                password = "s3cret-Generated_Password-0123456",
                keycloakUrl = "https://auth.example",
                realm = "blitzpay",
                clientId = "blitzpay-kitchen",
                apiBaseUrl = "https://api.example",
                branchId = branch.id,
            )
        )

        webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody()
            .jsonPath("$.username").isEqualTo("kitchen-br-1a2b3c4d5e6f")
            .jsonPath("$.password").isEqualTo("s3cret-Generated_Password-0123456")
            .jsonPath("$.keycloakUrl").isEqualTo("https://auth.example")
            .jsonPath("$.realm").isEqualTo("blitzpay")
            .jsonPath("$.clientId").isEqualTo("blitzpay-kitchen")
            .jsonPath("$.apiBaseUrl").isEqualTo("https://api.example")
            .jsonPath("$.branchId").isEqualTo(branch.id.toString())
    }

    @Test
    fun `disable returns the status without a password`() {
        whenever(kitchenAccessService.disable(any())).thenReturn(KitchenAccessStatusResponse(false, "kitchen-br-1a2b3c4d5e6f"))

        webTestClient.post().uri("$base/disable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
            .jsonPath("$.username").isEqualTo("kitchen-br-1a2b3c4d5e6f")
            .jsonPath("$.password").doesNotExist()
    }

    @Test
    fun `status returns enabled and username only`() {
        whenever(kitchenAccessService.status(any())).thenReturn(KitchenAccessStatusResponse(true, "kitchen-br-1a2b3c4d5e6f"))

        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(true)
            .jsonPath("$.username").isEqualTo("kitchen-br-1a2b3c4d5e6f")
            .jsonPath("$.password").doesNotExist()
    }

    @Test
    fun `callers who are not a Merchant Manager of the branch's merchant get 403`() {
        listOf(employee, "Bearer $fakeJwt").forEach { token ->
            webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isForbidden
                .expectBody().jsonPath("$.error").exists()
            webTestClient.post().uri("$base/disable").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
        }
        verify(kitchenAccessService, never()).enable(any())
        verify(kitchenAccessService, never()).disable(any())
    }

    @Test
    fun `unknown branch returns 404`() {
        val unknown = UUID.fromString("99999999-9999-9999-9999-999999999999")
        whenever(merchantBranchRepository.findById(unknown)).thenReturn(Optional.empty())

        webTestClient.post().uri("/v1/merchant/branches/$unknown/kitchen/enable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isNotFound
            .expectBody().jsonPath("$.error").isEqualTo("Branch not found: $unknown")
        webTestClient.get().uri("/v1/merchant/branches/$unknown/kitchen").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isNotFound
    }
}
