package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.BusinessProfile
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.persistence.model.PrimaryContact
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeofenceServiceTest {
    private val merchantRepo = mock<MerchantApplicationRepository>()
    private val branchRepo = mock<MerchantBranchRepository>()
    private val service = GeofenceService(merchantRepo, branchRepo)

    private fun merchant(
        status: MerchantOnboardingStatus = MerchantOnboardingStatus.ACTIVE,
        location: MerchantLocation? = MerchantLocation(48.8566, 2.3522, 150),
    ) = MerchantApplication(
        applicationReference = "REF-${UUID.randomUUID()}",
        businessProfile = BusinessProfile(
            legalBusinessName = "Test Merchant",
            businessType = "RETAIL",
            registrationNumber = "REG001",
            operatingCountry = "DE",
            primaryBusinessAddress = "Berlin"
        ),
        primaryContact = PrimaryContact("Test", "test@test.com", "+49111"),
        status = status,
    ).also { if (location != null) it.updateLocation(location) }

    private fun branch(
        active: Boolean = true,
        location: MerchantLocation? = MerchantLocation(48.8738, 2.2950, 100),
        merchantId: UUID = UUID.randomUUID(),
    ) = MerchantBranch(
        merchantApplicationId = merchantId,
        name = "Test Branch",
        active = active,
        location = location,
    )

    @Test
    fun `returns merchant region for active merchant with location`() {
        val m = merchant()
        whenever(merchantRepo.findAllByStatus(MerchantOnboardingStatus.ACTIVE)).thenReturn(listOf(m))
        whenever(branchRepo.findAllByActiveTrue()).thenReturn(emptyList())

        val result = service.buildRegions(null, null)

        assertEquals(1, result.regions.size)
        assertEquals("merchant:${m.id}", result.regions[0].regionId)
        assertEquals("MERCHANT", result.regions[0].regionType)
        assertEquals("Test Merchant", result.regions[0].displayName)
        assertEquals(150, result.regions[0].radiusMeters)
    }

    @Test
    fun `returns branch region for active branch with location`() {
        whenever(merchantRepo.findAllByStatus(MerchantOnboardingStatus.ACTIVE)).thenReturn(emptyList())
        val b = branch()
        whenever(branchRepo.findAllByActiveTrue()).thenReturn(listOf(b))

        val result = service.buildRegions(null, null)

        assertEquals(1, result.regions.size)
        assertEquals("branch:${b.id}", result.regions[0].regionId)
        assertEquals("BRANCH", result.regions[0].regionType)
        assertEquals("Test Branch", result.regions[0].displayName)
    }

    @Test
    fun `excludes merchant without location`() {
        val m = merchant(location = null)
        whenever(merchantRepo.findAllByStatus(MerchantOnboardingStatus.ACTIVE)).thenReturn(listOf(m))
        whenever(branchRepo.findAllByActiveTrue()).thenReturn(emptyList())

        val result = service.buildRegions(null, null)

        assertTrue(result.regions.isEmpty())
    }

    @Test
    fun `excludes branch without location`() {
        whenever(merchantRepo.findAllByStatus(MerchantOnboardingStatus.ACTIVE)).thenReturn(emptyList())
        val b = branch(location = null)
        whenever(branchRepo.findAllByActiveTrue()).thenReturn(listOf(b))

        val result = service.buildRegions(null, null)

        assertTrue(result.regions.isEmpty())
    }

    @Test
    fun `returns empty list when no active merchants or branches have location`() {
        whenever(merchantRepo.findAllByStatus(MerchantOnboardingStatus.ACTIVE)).thenReturn(emptyList())
        whenever(branchRepo.findAllByActiveTrue()).thenReturn(emptyList())

        val result = service.buildRegions(null, null)

        assertTrue(result.regions.isEmpty())
    }

    @Test
    fun `distanceMeters is null when no caller position provided`() {
        val m = merchant()
        whenever(merchantRepo.findAllByStatus(MerchantOnboardingStatus.ACTIVE)).thenReturn(listOf(m))
        whenever(branchRepo.findAllByActiveTrue()).thenReturn(emptyList())

        val result = service.buildRegions(null, null)

        assertNull(result.regions[0].distanceMeters)
    }

    @Test
    fun `sorts regions by distance when caller position provided`() {
        val near = branch(location = MerchantLocation(48.8566, 2.3522, 100))
        val far = branch(location = MerchantLocation(51.5074, -0.1278, 100))
        whenever(merchantRepo.findAllByStatus(MerchantOnboardingStatus.ACTIVE)).thenReturn(emptyList())
        whenever(branchRepo.findAllByActiveTrue()).thenReturn(listOf(far, near))

        val result = service.buildRegions(48.8566, 2.3522)

        assertNotNull(result.regions[0].distanceMeters)
        assertTrue(result.regions[0].distanceMeters!! < result.regions[1].distanceMeters!!)
        assertEquals("branch:${near.id}", result.regions[0].regionId)
    }

    @Test
    fun `includes distanceMeters in each region when caller position provided`() {
        val m = merchant()
        whenever(merchantRepo.findAllByStatus(MerchantOnboardingStatus.ACTIVE)).thenReturn(listOf(m))
        whenever(branchRepo.findAllByActiveTrue()).thenReturn(emptyList())

        val result = service.buildRegions(48.8566, 2.3522)

        assertNotNull(result.regions[0].distanceMeters)
    }
}
