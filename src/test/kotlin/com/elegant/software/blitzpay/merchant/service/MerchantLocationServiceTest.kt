package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.SetMerchantLocationRequest
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MerchantLocationServiceTest {
    private val repository = mock<MerchantRepository>()
    private val merchantBranchRepository = mock<MerchantBranchRepository>()
    private val service = MerchantLocationService(repository, merchantBranchRepository)

    private fun merchant() = Merchant(
        merchantName = "Location GmbH",
        businessType = "RETAIL",
        operatingCountry = "DE",
    )

    @Test
    fun `set location stores google place id for asynchronous enrichment`() {
        val m = merchant()
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }

        val response = service.setLocation(
            m.id,
            SetMerchantLocationRequest(
                latitude = 53.0758,
                longitude = 8.8072,
                geofenceRadiusMeters = 250,
                googlePlaceId = "ChIJ-test"
            )
        )

        assertEquals("ChIJ-test", response.googlePlaceId)
        assertEquals("PENDING", response.placeEnrichmentStatus)
    }

    @Test
    fun `find nearby returns merchant when only branch is within radius`() {
        val merchantId = UUID.randomUUID()
        val m = merchant().also { }

        val branch = MerchantBranch(
            merchantId = merchantId,
            name = "Nearby Branch",
            location = MerchantLocation(
                latitude = 53.1022777,
                longitude = 8.9146786,
                geofenceRadiusMeters = 150
            )
        )

        // Use a merchant with the same id as the branch's merchantId
        val merchantWithId = Merchant(
            merchantName = "Branch Nearby GmbH",
            businessType = "RETAIL",
            operatingCountry = "DE",
        )
        merchantWithId.originalMenuLanguage = "de"
        val branchWithMatchingId = MerchantBranch(
            merchantId = merchantWithId.id,
            name = "Nearby Branch",
            location = MerchantLocation(
                latitude = 53.1022777,
                longitude = 8.9146786,
                geofenceRadiusMeters = 150
            )
        )

        whenever(merchantBranchRepository.findAllByActiveTrue()).thenReturn(listOf(branchWithMatchingId))
        whenever(repository.findAllById(any<Set<UUID>>())).thenReturn(listOf(merchantWithId))

        val response = service.findNearby(53.1022777, 8.9146786, 500.0)

        assertEquals(1, response.merchants.size)
        assertEquals(merchantWithId.id, response.merchants.first().merchantId)
        assertNotNull(response.merchants.first().activeBranches.firstOrNull())
        assertEquals(branchWithMatchingId.id, response.merchants.first().activeBranches.first().branchId)
        assertEquals("de", response.merchants.first().activeBranches.first().originalMenuLanguage)
    }

    @Test
    fun `find nearby ignores merchant level location when no branch is within radius`() {
        val merchantId = UUID.randomUUID()
        val distantBranch = MerchantBranch(
            merchantId = merchantId,
            name = "Distant Branch",
            location = MerchantLocation(
                latitude = 53.2000000,
                longitude = 8.0000000,
                geofenceRadiusMeters = 150
            )
        )

        whenever(merchantBranchRepository.findAllByActiveTrue()).thenReturn(listOf(distantBranch))
        whenever(repository.findAllById(any<Set<UUID>>())).thenReturn(emptyList())

        val response = service.findNearby(53.1022777, 8.9146786, 500.0)

        assertEquals(0, response.merchants.size)
    }
}
