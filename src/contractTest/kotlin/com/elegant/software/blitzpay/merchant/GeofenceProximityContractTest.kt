package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.MerchantLocation
import com.elegant.software.blitzpay.merchant.domain.MerchantOfferingAssignment
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantProviderAccount
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.domain.ProximityEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Optional
import java.util.UUID

class GeofenceProximityContractTest : ContractVerifierBase() {

    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private val activeMerchant = MerchantApplication(
        id = merchantId,
        applicationReference = "BLTZ-GEO-001",
        businessProfile = BusinessProfile(
            legalBusinessName = "Geo Merchant GmbH",
            businessType = "RETAIL",
            registrationNumber = "GEO001",
            operatingCountry = "DE",
            primaryBusinessAddress = "Berlin"
        ),
        primaryContact = PrimaryContact("Max Geo", "max@geo.de", "+49111"),
        status = MerchantOnboardingStatus.ACTIVE,
    ).also {
        it.updateLocation(MerchantLocation(48.8566, 2.3522, 150))
    }

    private val activeBranch = MerchantBranch(
        id = branchId,
        merchantApplicationId = merchantId,
        name = "Geo Branch",
        active = true,
        location = MerchantLocation(48.8600, 2.3400, 100),
    )

    @BeforeEach
    fun setupGeofenceContracts() {
        whenever(merchantApplicationRepository.findAllByStatus(MerchantOnboardingStatus.ACTIVE))
            .thenReturn(listOf(activeMerchant))
        whenever(merchantApplicationRepository.findAllById(any<Set<UUID>>()))
            .thenReturn(listOf(activeMerchant))
        whenever(merchantBranchRepository.findAllByActiveTrue()).thenReturn(listOf(activeBranch))
        whenever(merchantApplicationRepository.findById(merchantId)).thenReturn(Optional.of(activeMerchant))
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(Optional.of(activeBranch))
        whenever(merchantBranchRepository.findAllByMerchantApplicationIdAndActiveTrue(merchantId))
            .thenReturn(listOf(activeBranch))
        whenever(proximityEventRepository.existsByRegionIdAndEventTypeAndUserSubjectAndReceivedAtAfter(
            any(), any(), any(), any()
        )).thenReturn(false)
        whenever(proximityEventRepository.existsByRegionIdAndEventTypeAndDeviceIdAndReceivedAtAfter(
            any(), any(), any(), any()
        )).thenReturn(false)
        whenever(proximityEventRepository.save(any<ProximityEvent>())).thenAnswer { it.arguments[0] }
        whenever(merchantProviderAccountRepository.findAllByMerchantApplicationId(merchantId))
            .thenReturn(listOf(MerchantProviderAccount(merchantApplicationId = merchantId, pspCode = "STRIPE", enabled = true)))
    }

    // ── GET /v1/geofence/regions ─────────────────────────────────────────────

    @Test
    fun `GET geofence regions returns 200 with region list`() {
        webTestClient.get()
            .uri("/v1/geofence/regions")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.regions").isArray
            .jsonPath("$.regions[0].regionId").exists()
            .jsonPath("$.regions[0].regionType").exists()
            .jsonPath("$.regions[0].sourceId").exists()
            .jsonPath("$.regions[0].displayName").exists()
            .jsonPath("$.regions[0].latitude").exists()
            .jsonPath("$.regions[0].longitude").exists()
            .jsonPath("$.regions[0].radiusMeters").exists()
    }

    @Test
    fun `GET geofence regions with position returns distanceMeters`() {
        webTestClient.get()
            .uri("/v1/geofence/regions?lat=48.8566&lng=2.3522")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.regions[0].distanceMeters").exists()
    }

    @Test
    fun `GET geofence regions returns empty list when no active located entities`() {
        whenever(merchantApplicationRepository.findAllByStatus(MerchantOnboardingStatus.ACTIVE))
            .thenReturn(emptyList())
        whenever(merchantBranchRepository.findAllByActiveTrue()).thenReturn(emptyList())

        webTestClient.get()
            .uri("/v1/geofence/regions")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.regions").isArray
            .jsonPath("$.regions").value<List<*>> { list -> assert(list.isEmpty()) }
    }

    // ── POST /v1/proximity ───────────────────────────────────────────────────

    @Test
    fun `POST proximity records enter event and returns merchant context`() {
        webTestClient.post()
            .uri("/v1/proximity")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "regionId": "merchant:$merchantId",
                  "event": "enter",
                  "location": { "latitude": 48.8566, "longitude": 2.3522 },
                  "timestamp": "2026-04-24T12:00:00Z",
                  "deviceId": "device-abc"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.recorded").isEqualTo(true)
            .jsonPath("$.action").isEqualTo("notify")
            .jsonPath("$.merchant.merchantId").isEqualTo(merchantId.toString())
            .jsonPath("$.merchant.name").exists()
            .jsonPath("$.merchant.activePspCodes").isArray
            .jsonPath("$.merchant.branches").isArray
    }

    @Test
    fun `POST proximity resolves merchant context from location when regionId is arbitrary`() {
        webTestClient.post()
            .uri("/v1/proximity")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "regionId": "home_001",
                  "event": "enter",
                  "location": { "latitude": 48.8600, "longitude": 2.3400 },
                  "timestamp": "2026-04-24T12:00:00Z"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.recorded").isEqualTo(true)
            .jsonPath("$.action").isEqualTo("notify")
            .jsonPath("$.merchant.merchantId").isEqualTo(merchantId.toString())
            .jsonPath("$.merchant.branches[0].branchId").isEqualTo(branchId.toString())
    }

    @Test
    fun `POST proximity deduplicates within cooldown window`() {
        whenever(proximityEventRepository.existsByRegionIdAndEventTypeAndDeviceIdAndReceivedAtAfter(
            any(), any(), any(), any()
        )).thenReturn(true)
        // With an authenticated request the controller uses user-subject-based dedup;
        // both paths must indicate duplicate for the response to be recorded=false.
        whenever(proximityEventRepository.existsByRegionIdAndEventTypeAndUserSubjectAndReceivedAtAfter(
            any(), any(), any(), any()
        )).thenReturn(true)

        webTestClient.post()
            .uri("/v1/proximity")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "regionId": "merchant:$merchantId",
                  "event": "enter",
                  "location": { "latitude": 48.8566, "longitude": 2.3522 },
                  "timestamp": "2026-04-24T12:00:00Z",
                  "deviceId": "device-abc"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.recorded").isEqualTo(false)
            .jsonPath("$.action").isEqualTo("none")
            .jsonPath("$.merchant").doesNotExist()
    }

    @Test
    fun `POST proximity exit event returns action none`() {
        webTestClient.post()
            .uri("/v1/proximity")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "regionId": "merchant:$merchantId",
                  "event": "exit",
                  "location": { "latitude": 48.8566, "longitude": 2.3522 },
                  "timestamp": "2026-04-24T12:00:05Z",
                  "deviceId": "device-abc"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.action").isEqualTo("none")
            .jsonPath("$.merchant").doesNotExist()
    }

    @Test
    fun `POST proximity returns deferredPaymentAvailable true when merchant has DEFERRED_PAYMENT offering`() {
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantApplicationId(merchantId))
            .thenReturn(listOf(MerchantOfferingAssignment(merchantId, "DEFERRED_PAYMENT")))

        webTestClient.post()
            .uri("/v1/proximity")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "regionId": "merchant:$merchantId",
                  "event": "enter",
                  "location": { "latitude": 48.8566, "longitude": 2.3522 },
                  "timestamp": "2026-05-15T10:00:00Z",
                  "deviceId": "device-deferred-1"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.merchant.deferredPaymentAvailable").isEqualTo(true)
    }

    @Test
    fun `POST proximity returns deferredPaymentAvailable false when merchant has no DEFERRED_PAYMENT offering`() {
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantApplicationId(merchantId))
            .thenReturn(listOf(MerchantOfferingAssignment(merchantId, "PRE_ORDER")))

        webTestClient.post()
            .uri("/v1/proximity")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "regionId": "merchant:$merchantId",
                  "event": "enter",
                  "location": { "latitude": 48.8566, "longitude": 2.3522 },
                  "timestamp": "2026-05-15T10:01:00Z",
                  "deviceId": "device-deferred-2"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.merchant.deferredPaymentAvailable").isEqualTo(false)
    }

    @Test
    fun `POST proximity returns 422 when required fields missing`() {
        webTestClient.post()
            .uri("/v1/proximity")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{ "event": "enter" }""")
            .exchange()
            .expectStatus().isEqualTo(422)
    }

    // ── GET /v1/merchants/nearby (enhanced) ─────────────────────────────────

    @Test
    fun `GET merchants nearby returns activeBranches per merchant`() {
        webTestClient.get()
            .uri("/v1/merchants/nearby?lat=48.8566&lng=2.3522&radiusMeters=1000")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.merchants[0].activeBranches").isArray
            .jsonPath("$.merchants[0].activeBranches[0].branchId").isEqualTo(branchId.toString())
    }

    @Test
    fun `GET merchants nearby returns empty merchants when no active located branches match radius`() {
        whenever(merchantBranchRepository.findAllByActiveTrue())
            .thenReturn(emptyList())

        webTestClient.get()
            .uri("/v1/merchants/nearby?lat=48.8566&lng=2.3522&radiusMeters=1000")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.merchants").isArray
            .jsonPath("$.merchants").value<List<*>> { list -> assert(list.isEmpty()) }
    }

    @Test
    fun `GET merchants nearby returns merchant when only branch location matches radius`() {
        val merchantWithoutLocation = MerchantApplication(
            id = merchantId,
            applicationReference = "BLTZ-GEO-002",
            businessProfile = BusinessProfile(
                legalBusinessName = "Branch Only Merchant GmbH",
                businessType = "RETAIL",
                registrationNumber = "GEO002",
                operatingCountry = "DE",
                primaryBusinessAddress = "Berlin"
            ),
            primaryContact = PrimaryContact("Max Geo", "max@geo.de", "+49111"),
            status = MerchantOnboardingStatus.ACTIVE,
        )

        whenever(merchantBranchRepository.findAllByActiveTrue())
            .thenReturn(listOf(activeBranch))
        whenever(merchantApplicationRepository.findAllById(any<Set<UUID>>()))
            .thenReturn(listOf(merchantWithoutLocation))

        webTestClient.get()
            .uri("/v1/merchants/nearby?lat=48.8600&lng=2.3400&radiusMeters=1000")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.merchants[0].merchantId").isEqualTo(merchantId.toString())
            .jsonPath("$.merchants[0].latitude").isEqualTo(48.8600)
            .jsonPath("$.merchants[0].longitude").isEqualTo(2.3400)
            .jsonPath("$.merchants[0].activeBranches[0].branchId").isEqualTo(branchId.toString())
    }
}
