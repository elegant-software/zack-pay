package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranchAppearance
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Optional
import java.util.UUID

class MerchantAppearanceContractTest : ContractVerifierBase() {
    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val start = Instant.now().plus(1, ChronoUnit.HOURS)
    private val branch = MerchantBranch(id = branchId, merchantId = merchantId, name = "Market Stop")
    private val merchant = Merchant(
        id = merchantId,
        merchantName = "Mobile GmbH",
        businessType = "RETAIL",
        operatingCountry = "DE",
        merchantMobility = MerchantMobility.MOBILE,
    )
    private val appearance = MerchantBranchAppearance(
        branchId = branchId,
        declaredLatitude = 52.52,
        declaredLongitude = 13.405,
        startTime = start,
        endTime = start.plus(6, ChronoUnit.HOURS),
        name = "Lunch stop",
    )

    @BeforeEach
    fun setup() {
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant))
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(Optional.of(branch))
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(userId)).thenReturn(
            listOf(MerchantUserAssignment(keycloakUserId = userId, merchantId = merchantId, role = MerchantOperationsRole.MERCHANT_MANAGER))
        )
        whenever(merchantBranchAppearanceRepository.findOverlapping(any(), any(), any())).thenReturn(emptyList())
        whenever(merchantBranchAppearanceRepository.save(any<MerchantBranchAppearance>())).thenAnswer { it.getArgument(0) }
        whenever(merchantBranchAppearanceRepository.findOperational(any(), any())).thenReturn(listOf(appearance))
        whenever(merchantBranchAppearanceRepository.findBranchHistory(any(), any(), any(), any(), any())).thenReturn(PageImpl(emptyList()))
    }

    @Test
    fun `manager can create and list branch appearances`() {
        webTestClient.post()
            .uri("/v1/merchants/$merchantId/branches/$branchId/appearances")
            .header("Authorization", bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"name":"Lunch stop","declaredLatitude":52.52,"declaredLongitude":13.405,"startTime":"$start","endTime":"${start.plus(6, ChronoUnit.HOURS)}"}"""
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("Lunch stop")
            .jsonPath("$.branchName").isEqualTo("Market Stop")
            .jsonPath("$.status").isEqualTo("UPCOMING")

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/branches/$branchId/appearances")
            .header("Authorization", bearer())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].name").isEqualTo("Lunch stop")
    }

    @Test
    fun `branch history returns paginated contract`() {
        webTestClient.get()
            .uri("/v1/merchants/$merchantId/branches/$branchId/appearances/history?from=2025-06-11T00:00:00Z&to=2026-06-11T23:59:59Z&page=0&size=20")
            .header("Authorization", bearer())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0)
    }

    @Test
    fun `appearance management rejects missing authorization`() {
        webTestClient.get()
            .uri("/v1/merchants/$merchantId/branches/$branchId/appearances")
            .exchange()
            .expectStatus().is4xxClientError
    }

    private fun bearer(): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$userId"}""".toByteArray())
        return "Bearer header.$payload.signature"
    }
}
