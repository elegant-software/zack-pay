package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.SetMerchantLocationRequest
import com.elegant.software.blitzpay.merchant.persistence.model.BusinessProfile
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation
import com.elegant.software.blitzpay.merchant.persistence.model.PrimaryContact
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MerchantLocationServiceTest {
    private val repository = mock<MerchantApplicationRepository>()
    private val merchantBranchRepository = mock<MerchantBranchRepository>()
    private val service = MerchantLocationService(repository, merchantBranchRepository)

    @Test
    fun `set location stores google place id for asynchronous enrichment`() {
        val merchant = MerchantApplication(
            applicationReference = "BLTZ-LOC",
            businessProfile = BusinessProfile(
                legalBusinessName = "Location GmbH",
                businessType = "RETAIL",
                registrationNumber = "DE-LOC",
                operatingCountry = "DE",
                primaryBusinessAddress = "Legal Strasse 1"
            ),
            primaryContact = PrimaryContact("Jane Doe", "jane@example.com", "+491234")
        )
        whenever(repository.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(repository.save(any<MerchantApplication>())).thenAnswer { it.arguments[0] }

        val response = service.setLocation(
            merchant.id,
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
        val merchant = MerchantApplication(
            applicationReference = "BLTZ-NEARBY",
            businessProfile = BusinessProfile(
                legalBusinessName = "Branch Nearby GmbH",
                businessType = "RETAIL",
                registrationNumber = "DE-NEARBY",
                operatingCountry = "DE",
                primaryBusinessAddress = "Legal Strasse 2"
            ),
            primaryContact = PrimaryContact("Jane Doe", "jane@example.com", "+491234")
        )

        val branch = MerchantBranch(
            merchantApplicationId = merchant.id,
            name = "Nearby Branch",
            location = MerchantLocation(
                latitude = 53.1022777,
                longitude = 8.9146786,
                geofenceRadiusMeters = 150
            )
        )

        whenever(merchantBranchRepository.findAllByActiveTrue()).thenReturn(listOf(branch))
        whenever(repository.findAllById(any<Set<java.util.UUID>>())).thenReturn(listOf(merchant))

        val response = service.findNearby(53.1022777, 8.9146786, 500.0)

        assertEquals(1, response.merchants.size)
        assertEquals(merchant.id, response.merchants.first().merchantId)
        assertNotNull(response.merchants.first().activeBranches.firstOrNull())
        assertEquals(branch.id, response.merchants.first().activeBranches.first().branchId)
    }

    @Test
    fun `find nearby ignores merchant level location when no branch is within radius`() {
        val merchant = MerchantApplication(
            applicationReference = "BLTZ-NEARBY-MERCHANT-ONLY",
            businessProfile = BusinessProfile(
                legalBusinessName = "Merchant Only GmbH",
                businessType = "RETAIL",
                registrationNumber = "DE-NEARBY-MERCHANT",
                operatingCountry = "DE",
                primaryBusinessAddress = "Legal Strasse 3"
            ),
            primaryContact = PrimaryContact("Jane Doe", "jane@example.com", "+491234")
        ).also {
            it.updateLocation(MerchantLocation(53.1022777, 8.9146786, 150))
        }

        val distantBranch = MerchantBranch(
            merchantApplicationId = merchant.id,
            name = "Distant Branch",
            location = MerchantLocation(
                latitude = 53.2000000,
                longitude = 8.0000000,
                geofenceRadiusMeters = 150
            )
        )

        whenever(merchantBranchRepository.findAllByActiveTrue()).thenReturn(listOf(distantBranch))
        whenever(repository.findAllById(any<Set<java.util.UUID>>())).thenReturn(listOf(merchant))

        val response = service.findNearby(53.1022777, 8.9146786, 500.0)

        assertEquals(0, response.merchants.size)
    }
}
