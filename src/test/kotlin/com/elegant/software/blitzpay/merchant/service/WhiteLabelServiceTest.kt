package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.*
import com.elegant.software.blitzpay.merchant.persistence.model.*
import com.elegant.software.blitzpay.merchant.persistence.repository.*
import com.elegant.software.blitzpay.merchant.support.MerchantTestFixtureLoader
import com.elegant.software.blitzpay.storage.StorageService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.Optional
import kotlin.test.*

class WhiteLabelServiceTest {
    private val merchants = mock<MerchantRepository>()
    private val branches = mock<MerchantBranchRepository>()
    private val profiles = mock<MerchantWhiteLabelRepository>()
    private val assignments = mock<MerchantOfferingAssignmentRepository>()
    private val offerings = mock<MerchantOfferingRepository>()
    private val storage = mock<StorageService>()
    private val branding = MerchantBrandingService(merchants, branches, storage)
    private val service = WhiteLabelService(merchants, branches, profiles, assignments, offerings, branding, storage)
    private val merchant = MerchantTestFixtureLoader.merchant()
    private val branch = MerchantBranch(merchantId = merchant.id, name = "Second location")
    private val settings: WhiteLabelSettings = requireNotNull(javaClass.getResourceAsStream("/testdata/merchant/white-label.json"))
        .use { jacksonObjectMapper().readValue(it) }

    @BeforeEach
    fun setup() {
        whenever(merchants.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(branches.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(profiles.saveAndFlush(any<MerchantWhiteLabel>())).thenAnswer { it.arguments[0] }
        whenever(assignments.findAllByMerchantId(merchant.id)).thenReturn(listOf(
            MerchantOfferingAssignment(merchant.id, "APPOINTMENT_BOOKING"), MerchantOfferingAssignment(merchant.id, "PRE_ORDER")))
        whenever(offerings.findAllByActiveTrueOrderByDisplayNameAsc()).thenReturn(listOf(
            MerchantOffering("APPOINTMENT_BOOKING", "Appointments"), MerchantOffering("PRE_ORDER", "Pre-order")))
    }

    @Test
    fun `missing configuration resolves defaults without creating data`() {
        val response = service.get(merchant.id, null, ThemeApp.KITCHEN)
        assertFalse(response.effective.enabled)
        assertEquals(merchant.merchantName, response.effective.displayName)
        assertEquals(setOf("APPOINTMENT_BOOKING", "PRE_ORDER"), response.effective.features)
        assertEquals(ThemeMode.SYSTEM, response.effective.themeMode)
        verify(profiles, never()).saveAndFlush(any())
    }

    @Test
    fun `branch inherits merchant app settings and cannot reenable hidden features`() {
        whenever(profiles.findByMerchantIdAndBranchIdAndApp(merchant.id, null, ThemeApp.STOREFRONT))
            .thenReturn(MerchantWhiteLabel(merchantId = merchant.id, app = ThemeApp.STOREFRONT, settings = settings))
        whenever(profiles.findByMerchantIdAndBranchIdAndApp(merchant.id, branch.id, ThemeApp.STOREFRONT))
            .thenReturn(MerchantWhiteLabel(merchantId = merchant.id, branchId = branch.id, app = ThemeApp.STOREFRONT,
                settings = WhiteLabelSettings(title = "Branch title")))
        val result = service.get(merchant.id, branch.id, ThemeApp.STOREFRONT).effective
        assertTrue(result.enabled)
        assertEquals("Studio North", result.displayName)
        assertEquals("Branch title", result.title)
        assertEquals(setOf("APPOINTMENT_BOOKING"), result.features)
        assertFalse(service.get(merchant.id, null, ThemeApp.KITCHEN).effective.enabled)
    }

    @Test
    fun `disabled merchant cannot be enabled by branch`() {
        whenever(profiles.findByMerchantIdAndBranchIdAndApp(merchant.id, null, ThemeApp.SHOPPER))
            .thenReturn(MerchantWhiteLabel(merchantId = merchant.id, app = ThemeApp.SHOPPER, settings = settings.copy(enabled = false)))
        whenever(profiles.findByMerchantIdAndBranchIdAndApp(merchant.id, branch.id, ThemeApp.SHOPPER))
            .thenReturn(MerchantWhiteLabel(merchantId = merchant.id, branchId = branch.id, app = ThemeApp.SHOPPER, settings = settings))
        assertFalse(service.get(merchant.id, branch.id, ThemeApp.SHOPPER).effective.enabled)
    }

    @Test
    fun `editor exposes inherited palette without a local override and hides inactive offerings`() {
        whenever(profiles.findByMerchantIdAndBranchIdAndApp(merchant.id, null, ThemeApp.STOREFRONT))
            .thenReturn(MerchantWhiteLabel(merchantId = merchant.id, app = ThemeApp.STOREFRONT, settings = settings))
        whenever(profiles.findByMerchantIdAndBranchIdAndApp(merchant.id, branch.id, ThemeApp.STOREFRONT))
            .thenReturn(MerchantWhiteLabel(merchantId = merchant.id, branchId = branch.id, app = ThemeApp.STOREFRONT,
                settings = WhiteLabelSettings(light = settings.light!!.copy(card = "#F0F0F0"))))
        whenever(offerings.findAllByActiveTrueOrderByDisplayNameAsc()).thenReturn(listOf(MerchantOffering("APPOINTMENT_BOOKING", "Appointments")))
        val result = service.get(merchant.id, branch.id, ThemeApp.STOREFRONT)
        assertEquals("#F0F0F0", result.effective.light.card)
        assertEquals(settings.light!!.card, result.inheritedConfiguration!!.light.card)
        assertEquals(setOf("APPOINTMENT_BOOKING"), result.availableFeatures)
        assertEquals(setOf("APPOINTMENT_BOOKING"), result.effective.features)
    }

    @Test
    fun `branch from another tenant is rejected before write`() {
        val alien = MerchantBranch(merchantId = java.util.UUID.randomUUID(), name = "Other merchant")
        whenever(branches.findById(alien.id)).thenReturn(Optional.of(alien))
        assertFailsWith<NoSuchElementException> { service.replace(merchant.id, alien.id, ThemeApp.KITCHEN, WhiteLabelUpdate(settings)) }
        verify(profiles, never()).saveAndFlush(any())
    }

    @Test
    fun `publish validates palettes scope logos and revision`() {
        assertFailsWith<IllegalArgumentException> { service.replace(merchant.id, null, ThemeApp.MERCHANT,
            WhiteLabelUpdate(settings.copy(light = ThemePalette(background = "#FFFFFF", onBackground = "#FFFFFF")))) }
        assertFailsWith<IllegalArgumentException> { service.replace(merchant.id, null, ThemeApp.MERCHANT,
            WhiteLabelUpdate(settings.copy(logoStorageKey = "merchants/other/branding/logo.png"))) }
        val saved = MerchantWhiteLabel(merchantId = merchant.id, app = ThemeApp.MERCHANT, settings = settings, revision = 3)
        whenever(profiles.findByMerchantIdAndBranchIdAndApp(merchant.id, null, ThemeApp.MERCHANT)).thenReturn(saved)
        assertFailsWith<WhiteLabelConflictException> { service.replace(merchant.id, null, ThemeApp.MERCHANT, WhiteLabelUpdate(settings)) }
        assertEquals(settings.title, service.replace(merchant.id, null, ThemeApp.MERCHANT, WhiteLabelUpdate(settings, 3)).settings.title)
    }
}
