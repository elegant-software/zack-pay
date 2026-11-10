package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.BranchNameUpdated
import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.UpdateBranchRequest
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantEntityStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation
import com.elegant.software.blitzpay.merchant.persistence.model.PostalAddress
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantBranchServiceTest {
    private val branchRepository = mock<MerchantBranchRepository>()
    private val merchantRepository = mock<MerchantRepository>()
    private val storageService = mock<StorageService>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val geofenceProperties = mock<com.elegant.software.blitzpay.merchant.config.GeofenceProperties>().also {
        whenever(it.defaultRadiusMeters).thenReturn(500)
    }
    private val service = MerchantBranchService(branchRepository, merchantRepository, storageService, eventPublisher, geofenceProperties, java.util.Optional.empty())

    private fun merchant(name: String = "Test Merchant GmbH") = Merchant(
        merchantName = name,
        businessType = "RETAIL",
        operatingCountry = "DE",
    )

    @Test
    fun `branches inherit current merchant original language`() {
        val m = merchant().apply { originalMenuLanguage = "de" }
        whenever(merchantRepository.existsById(m.id)).thenReturn(true)
        whenever(merchantRepository.findById(m.id)).thenReturn(Optional.of(m))
        val branches = listOf(MerchantBranch(merchantId = m.id, name = "One"), MerchantBranch(merchantId = m.id, name = "Two"))
        whenever(branchRepository.findAllByMerchantId(m.id)).thenReturn(branches)
        whenever(branchRepository.findById(branches[0].id)).thenReturn(Optional.of(branches[0]))
        assertEquals(listOf("de", "de"), service.list(m.id).map { it.originalMenuLanguage })
        m.originalMenuLanguage = "en"
        assertEquals("en", service.get(m.id, branches[0].id).originalMenuLanguage)
    }

    @Test
    fun `create branch stores operational address and geolocation`() {
        val m = merchant()
        whenever(merchantRepository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(branchRepository.save(any<MerchantBranch>())).thenAnswer { it.arguments[0] }

        val response = service.create(
            m.id,
            CreateBranchRequest(
                name = "Bremen Mitte",
                addressLine1 = "Marktplatz 1",
                city = "Bremen",
                postalCode = "28195",
                country = "DE",
                latitude = 53.0758,
                longitude = 8.8072,
                geofenceRadiusMeters = 250,
                googlePlaceId = "ChIJ-test"
            )
        )

        assertEquals("Marktplatz 1", response.addressLine1)
        assertEquals("Bremen", response.city)
        assertEquals(53.0758, response.latitude)
        assertEquals(8.8072, response.longitude)
        assertEquals(250, response.geofenceRadiusMeters)
        assertEquals("ChIJ-test", response.googlePlaceId)
        assertEquals("PENDING", response.placeEnrichmentStatus)
    }

    @Test
    fun `create branch publishes BranchCreated event for active branch`() {
        val m = merchant("Acme GmbH")
        whenever(merchantRepository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(branchRepository.save(any<MerchantBranch>())).thenAnswer { it.arguments[0] }

        service.create(
            m.id,
            CreateBranchRequest(
                name = "Main Branch",
                addressLine1 = "High Street 1",
                city = "Berlin",
                postalCode = "10115",
                country = "DE",
                latitude = 52.52,
                longitude = 13.405,
                geofenceRadiusMeters = 150
            )
        )

        val captor = argumentCaptor<BranchCreated>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(m.id, captor.firstValue.merchantId)
        assertEquals("Main Branch", captor.firstValue.branchName)
        assertEquals("Acme GmbH", captor.firstValue.merchantName)
    }

    @Test
    fun `create can persist inactive branch`() {
        val m = merchant()
        whenever(merchantRepository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(branchRepository.save(any<MerchantBranch>())).thenAnswer { it.arguments[0] }

        val response = service.create(
            m.id,
            CreateBranchRequest(name = "MCP Branch"),
            active = false
        )

        assertEquals(false, response.active)
        assertEquals("INACTIVE", response.status)
        verify(eventPublisher, never()).publishEvent(any<BranchCreated>())
    }

    @Test
    fun `list returns active and inactive branches`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(branchRepository.findAllByMerchantId(merchantId)).thenReturn(
            listOf(
                MerchantBranch(merchantId = merchantId, name = "Active Branch", active = true),
                MerchantBranch(merchantId = merchantId, name = "Inactive Branch", active = false),
            )
        )

        val response = service.list(merchantId)

        assertEquals(2, response.size)
        assertTrue(response.any { it.name == "Active Branch" && it.active })
        assertTrue(response.any { it.name == "Inactive Branch" && !it.active })
        verify(branchRepository).findAllByMerchantId(merchantId)
    }

    @Test
    fun `upsertByName preserves active status for existing active branch`() {
        val merchantId = UUID.randomUUID()
        val existingBranch = MerchantBranch(
            merchantId = merchantId,
            name = "Main Branch",
            active = true,
            status = MerchantEntityStatus.ACTIVE,
            address = PostalAddress(
                addressLine1 = "Old Street 1",
                city = "Berlin",
                postalCode = "10115",
                country = "DE",
            ),
        ).also {
            it.location = MerchantLocation(latitude = 52.52, longitude = 13.405, geofenceRadiusMeters = 150)
        }
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(branchRepository.findByNameAndMerchantId("Main Branch", merchantId)).thenReturn(existingBranch)
        whenever(branchRepository.save(any<MerchantBranch>())).thenAnswer { it.arguments[0] }

        val response = service.upsertByName(
            merchantId = merchantId,
            branchName = "Main Branch",
            addressLine1 = "New Street 2",
            city = "Berlin",
            postalCode = "10117",
            country = "DE",
            latitude = 52.521,
            longitude = 13.406,
            geofenceRadiusMeters = 175,
        )

        assertEquals(true, response.active)
        assertEquals("ACTIVE", response.status)

        val branchCaptor = argumentCaptor<MerchantBranch>()
        verify(branchRepository).save(branchCaptor.capture())
        assertEquals(true, branchCaptor.firstValue.active)
        assertEquals(MerchantEntityStatus.ACTIVE, branchCaptor.firstValue.status)
    }

    @Test
    fun `update branch stores contact and activation state`() {
        val merchantId = UUID.randomUUID()
        val branch = MerchantBranch(
            merchantId = merchantId,
            name = "Bremen Mitte",
            address = PostalAddress(
                addressLine1 = "Old Strasse 1",
                city = "Bremen",
                postalCode = "28195",
                country = "DE",
            ),
        ).also {
            it.location = MerchantLocation(latitude = 53.0758, longitude = 8.8072, geofenceRadiusMeters = 250)
        }
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant()))
        whenever(branchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(branchRepository.saveAndFlush(any<MerchantBranch>())).thenAnswer { it.arguments[0] }

        val response = service.update(
            merchantId,
            branch.id,
            UpdateBranchRequest(
                name = "Bremen Nord",
                active = false,
                addressLine1 = "Neue Strasse 1",
                city = "Bremen",
                postalCode = "28195",
                country = "DE",
                contactFullName = "Branch Lead",
                website = "https://branch.example.com",
                contactEmail = "branch@example.com",
                contactPhoneNumber = "+4930123456",
                latitude = 53.0758,
                longitude = 8.8072,
                geofenceRadiusMeters = 250,
                capacity = 60,
            )
        )

        assertEquals(60, response.capacity)
        assertEquals("Bremen Nord", response.name)
        assertEquals(false, response.active)
        assertEquals("Branch Lead", response.contactFullName)
        assertEquals("https://branch.example.com", response.website)
        assertEquals("branch@example.com", response.contactEmail)
        assertEquals("+4930123456", response.contactPhoneNumber)

        val eventCaptor = argumentCaptor<BranchNameUpdated>()
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertEquals(branch.id, eventCaptor.firstValue.branchId)
        assertEquals(merchantId, eventCaptor.firstValue.merchantId)
        assertEquals("Bremen Nord", eventCaptor.firstValue.newName)
    }

    @Test
    fun `update branch does not publish BranchNameUpdated when name unchanged`() {
        val merchantId = UUID.randomUUID()
        val branch = MerchantBranch(
            merchantId = merchantId,
            name = "Same Name",
            address = PostalAddress(
                addressLine1 = "Same Street 1",
                city = "Hamburg",
                postalCode = "20095",
                country = "DE",
            ),
        ).also {
            it.location = MerchantLocation(latitude = 53.5511, longitude = 9.9937, geofenceRadiusMeters = 200)
        }
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant()))
        whenever(branchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(branchRepository.saveAndFlush(any<MerchantBranch>())).thenAnswer { it.arguments[0] }

        service.update(
            merchantId, branch.id,
            UpdateBranchRequest(
                name = "Same Name", active = true, contactFullName = "New Contact",
                contactEmail = "new@example.com", contactPhoneNumber = "+49999",
                addressLine1 = "Same Street 1",
                city = "Hamburg",
                postalCode = "20095",
                country = "DE",
                latitude = 53.5511,
                longitude = 9.9937,
                geofenceRadiusMeters = 200
            )
        )

        verify(eventPublisher, never()).publishEvent(any<BranchNameUpdated>())
    }
}
