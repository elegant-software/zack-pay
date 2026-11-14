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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
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

    private val adminId = UUID.fromString("77777777-7777-7777-7777-777777777777")

    private fun bearer(sub: UUID, realmRoles: List<String>? = null): String {
        val roles = realmRoles?.joinToString(",", prefix = ""","realm_access":{"roles":[""", postfix = "]}") { "\"$it\"" }.orEmpty()
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$sub"$roles}""".toByteArray())
        return "Bearer fakeheader.$payload.fakesig"
    }

    private val manager = bearer(managerId)
    private val employee = bearer(employeeId)
    /** A platform admin (the web admin's `blitzpay-admin` realm role) with no Merchant assignment. */
    private val platformAdmin = bearer(adminId, listOf("offline_access", "blitzpay-admin"))
    private val otherRealmRole = bearer(adminId, listOf("offline_access", "uma_authorization"))

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
        whenever(kitchenAccessService.enable(any(), anyOrNull())).thenReturn(
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
    fun `callers who are neither a Merchant Manager of the branch's merchant nor a platform admin get 403`() {
        listOf(employee, otherRealmRole, "Bearer $fakeJwt").forEach { token ->
            webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isForbidden
                .expectBody().jsonPath("$.detail").exists()
            webTestClient.post().uri("$base/disable").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
        }
        verify(kitchenAccessService, never()).enable(any(), anyOrNull())
        verify(kitchenAccessService, never()).disable(any())
    }

    @Test
    fun `a platform admin without a Merchant assignment can read, enable and disable the kitchen login`() {
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(adminId)).thenReturn(emptyList())
        whenever(kitchenAccessService.status(any())).thenReturn(KitchenAccessStatusResponse(true, "kitchen-br-1a2b3c4d5e6f"))
        whenever(kitchenAccessService.enable(any(), anyOrNull())).thenReturn(
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
        whenever(kitchenAccessService.disable(any())).thenReturn(KitchenAccessStatusResponse(false, "kitchen-br-1a2b3c4d5e6f"))

        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(true)
            .jsonPath("$.username").isEqualTo("kitchen-br-1a2b3c4d5e6f")
        webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody().jsonPath("$.password").isEqualTo("s3cret-Generated_Password-0123456")
        webTestClient.post().uri("$base/disable").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.enabled").isEqualTo(false)
        verify(kitchenAccessService).enable(branch)
        verify(kitchenAccessService).disable(branch)
    }

    @Test
    fun `enable passes the password the admin chose`() {
        whenever(kitchenAccessService.enable(any(), anyOrNull())).thenThrow(IllegalStateException("unexpected"))
        whenever(kitchenAccessService.enable(any(), eq("test-pass-1"))).thenReturn(
            KitchenLoginResponse("altstadt-kitchen", "test-pass-1", "https://auth.example", "blitzpay", "blitzpay-kitchen", "https://api.example", branch.id),
        )
        webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue("""{"password":"test-pass-1"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.username").isEqualTo("altstadt-kitchen")
    }

    @Test
    fun `unknown branch returns 404 for a platform admin too`() {
        val unknown = UUID.fromString("99999999-9999-9999-9999-999999999999")
        whenever(merchantBranchRepository.findById(unknown)).thenReturn(Optional.empty())

        webTestClient.get().uri("/v1/merchant/branches/$unknown/kitchen").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `unknown branch returns 404`() {
        val unknown = UUID.fromString("99999999-9999-9999-9999-999999999999")
        whenever(merchantBranchRepository.findById(unknown)).thenReturn(Optional.empty())

        webTestClient.post().uri("/v1/merchant/branches/$unknown/kitchen/enable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isNotFound
            .expectBody().jsonPath("$.detail").isEqualTo("Branch not found: $unknown")
        webTestClient.get().uri("/v1/merchant/branches/$unknown/kitchen").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `keycloak 403 on enable returns 502 with an actionable error`() {
        whenever(kitchenAccessService.enable(any(), anyOrNull())).thenThrow(jakarta.ws.rs.ForbiddenException())

        webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody().jsonPath("$.detail").value<String> { assert(it.contains("manage-clients")) }
    }
}
