package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.api.MerchantOfferingResponse
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOffering
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOfferingAssignment
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class MerchantManagementServiceTest {

    private val repository = mock<MerchantRepository>()
    private val branchRepository = mock<MerchantBranchRepository>()
    private val merchantOfferingRepository = mock<MerchantOfferingRepository>()
    private val merchantOfferingAssignmentRepository = mock<MerchantOfferingAssignmentRepository>()
    private val storageService = mock<StorageService>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val tableService = mock<MerchantTableService>()
    private val storefrontThemeService = mock<StorefrontThemeService>()
    private val service = MerchantManagementService(
        repository,
        branchRepository,
        merchantOfferingRepository,
        merchantOfferingAssignmentRepository,
        storageService,
        eventPublisher,
        mock<MenuLanguageService>(),
        tableService,
        storefrontThemeService,
    )

    private fun merchant(name: String = "Old Name GmbH") = Merchant(
        merchantName = name,
        businessType = "RETAIL",
        operatingCountry = "DE",
    )

    @Test
    fun `update merchant changes editable fields and status`() {
        val m = merchant()
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantId(m.id)).thenReturn(emptyList())

        val response = service.update(
            m.id,
            UpdateMerchantRequest(
                merchantName = "New Name GmbH",
                merchantStatus = MerchantOperationalStatus.INACTIVE,
            )
        )

        assertEquals("New Name GmbH", response.merchantName)
        assertEquals(MerchantOperationalStatus.INACTIVE, response.merchantStatus)
    }

    @Test
    fun `update merchant changes merchant mobility`() {
        val m = merchant("Mobile Merchant GmbH")
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantId(m.id)).thenReturn(emptyList())

        val response = service.update(
            m.id,
            UpdateMerchantRequest(merchantMobility = MerchantMobility.MOBILE)
        )

        assertEquals(MerchantMobility.MOBILE, m.merchantMobility)
        assertEquals(MerchantMobility.MOBILE, response.merchantMobility)
    }

    @Test
    fun `update merchant changes storefront theme and keeps it when omitted`() {
        val m = merchant("Cafe GmbH")
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantId(m.id)).thenReturn(emptyList())

        val response = service.update(m.id, UpdateMerchantRequest(storefrontTheme = "HERITAGE"))
        assertEquals("HERITAGE", response.storefrontTheme)
        verify(storefrontThemeService).requireSelectable("HERITAGE")

        service.update(m.id, UpdateMerchantRequest(merchantName = "Cafe GmbH"))
        assertEquals("HERITAGE", m.storefrontTheme)
    }

    @Test
    fun `update merchant rejects a theme that is not published`() {
        val m = merchant("Cafe GmbH")
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(storefrontThemeService.requireSelectable("OCEAN")).thenThrow(IllegalArgumentException("not published"))

        assertThrows<IllegalArgumentException> { service.update(m.id, UpdateMerchantRequest(storefrontTheme = "OCEAN")) }
        assertEquals("DEFAULT", m.storefrontTheme)
    }

    @Test
    fun `update publishes MerchantNameUpdated when name changes`() {
        val m = merchant("Old Name GmbH")
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantId(m.id)).thenReturn(emptyList())

        service.update(m.id, UpdateMerchantRequest(merchantName = "New Name GmbH"))

        val captor = argumentCaptor<MerchantNameUpdated>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(m.id, captor.firstValue.merchantId)
        assertEquals("New Name GmbH", captor.firstValue.newName)
    }

    @Test
    fun `update does not publish MerchantNameUpdated when name unchanged`() {
        val m = merchant("Same Name GmbH")
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantId(m.id)).thenReturn(emptyList())

        service.update(m.id, UpdateMerchantRequest(merchantName = "Same Name GmbH"))

        verify(eventPublisher, never()).publishEvent(any<MerchantNameUpdated>())
    }

    @Test
    fun `deleteMerchant deletes branches before the merchant`() {
        val m = merchant("To Delete GmbH")
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))

        service.deleteMerchant(m.id)

        inOrder(tableService, branchRepository, repository) {
            verify(tableService).deleteAllForBranches(any())
            verify(branchRepository).deleteAllByMerchantId(m.id)
            verify(repository).delete(m)
        }
    }

    @Test
    fun `update merchant syncs offerings`() {
        val m = merchant("Offer Merchant")
        whenever(repository.findById(m.id)).thenReturn(Optional.of(m))
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }
        whenever(merchantOfferingRepository.findAllByActiveTrueOrderByDisplayNameAsc()).thenReturn(
            listOf(
                MerchantOffering("PRE_ORDER", "Pre-Order"),
                MerchantOffering("WALK_IN_ORDERING", "Walk-In Ordering"),
                MerchantOffering("DEFERRED_PAYMENT", "Deferred Payment"),
            )
        )
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantId(m.id)).thenReturn(emptyList())

        val response = service.update(
            m.id,
            UpdateMerchantRequest(offerings = setOf("PRE_ORDER", "DEFERRED_PAYMENT"))
        )

        verify(merchantOfferingAssignmentRepository).saveAll(any<List<MerchantOfferingAssignment>>())
        assertEquals("Offer Merchant", response.merchantName)
    }
}
