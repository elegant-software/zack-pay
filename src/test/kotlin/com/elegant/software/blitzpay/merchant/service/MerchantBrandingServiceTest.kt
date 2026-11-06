package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.BrandingSettings
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranding
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.support.MerchantTestFixtureLoader
import com.elegant.software.blitzpay.storage.StorageService
import com.elegant.software.blitzpay.storage.PresignedUpload
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.Optional
import java.util.UUID
import kotlin.test.*

class MerchantBrandingServiceTest {
    @Test
    fun `disabled branding exposes a saved logo preview without changing shopper identity`() {
        merchant.branding = MerchantBranding(false, logoStorageKey = "saved-logo.png")
        val response = service.get(merchant.id)
        assertEquals("https://images.example/saved-logo.png", response.logoPreviewUrl)
        assertNull(response.effective.logoUrl)
    }

    private val merchants = mock<MerchantRepository>()
    private val branches = mock<MerchantBranchRepository>()
    private val storage = mock<StorageService>()
    private val service = MerchantBrandingService(merchants, branches, storage)
    private val merchant = MerchantTestFixtureLoader.merchant()
    private val branch = MerchantBranch(merchantId = merchant.id, name = "Branch")

    @BeforeEach
    fun setup() {
        whenever(merchants.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(branches.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(storage.presignDownload(any(), any())).thenAnswer { "https://images.example/${it.arguments[0]}" }
    }

    @Test
    fun `unconfigured merchant uses Simpleio styling and ordinary identity`() {
        merchant.logoStorageKey = "ordinary-logo.png"
        val result = service.get(merchant.id).effective
        assertFalse(result.enabled)
        assertEquals("Simpleio", result.platformName)
        assertEquals(merchant.merchantName, result.displayName)
        assertEquals("https://images.example/ordinary-logo.png", result.logoUrl)
        assertEquals(MerchantBrandingService.DEFAULT_PRIMARY, result.primaryColor)
    }

    @Test
    fun `branch overrides individual fields and inherits missing merchant fields`() {
        merchant.branding = MerchantBranding(true, "Parent", primaryColor = "#000000", accentColor = "#FFFFFF")
        branch.branding = MerchantBranding(displayName = "Child", accentColor = "#123456")
        val response = service.get(merchant.id, branch.id)
        assertEquals("Child", response.effective.displayName)
        assertEquals("#000000", response.effective.primaryColor)
        assertEquals("#FFFFFF", response.effective.primaryTextColor)
        assertEquals("#123456", response.effective.accentColor)
        assertNull(response.settings.primaryColor)
    }

    @Test
    fun `disabled merchant suppresses branch overrides without deleting them`() {
        merchant.branding = MerchantBranding(false, "Parent")
        branch.branding = MerchantBranding(true, "Child", primaryColor = "#000000")
        val response = service.get(merchant.id, branch.id)
        assertFalse(response.effective.enabled)
        assertEquals(merchant.merchantName, response.effective.displayName)
        assertEquals("Child", response.settings.displayName)
    }

    @Test
    fun `branch can disable custom branding`() {
        merchant.branding = MerchantBranding(true, "Parent")
        branch.branding = MerchantBranding(false)
        assertFalse(service.get(merchant.id, branch.id).effective.enabled)
    }

    @Test
    fun `replacement clears overrides and never changes legal merchant name`() {
        merchant.branding = MerchantBranding(true, "Custom")
        val original = merchant.merchantName
        service.replace(merchant.id, null, MerchantTestFixtureLoader.brandingSettings())
        assertEquals(original, merchant.merchantName)
        service.replace(merchant.id, null, BrandingSettings())
        assertNull(merchant.branding?.displayName)
        assertFalse(service.get(merchant.id).effective.enabled)
    }

    @Test
    fun `branch from another merchant is inaccessible for reads writes and uploads`() {
        val alien = MerchantBranch(merchantId = UUID.randomUUID(), name = "Other")
        whenever(branches.findById(alien.id)).thenReturn(Optional.of(alien))
        assertFailsWith<NoSuchElementException> { service.get(merchant.id, alien.id) }
        assertFailsWith<NoSuchElementException> { service.replace(merchant.id, alien.id, BrandingSettings()) }
        assertFailsWith<NoSuchElementException> { service.createUploadUrl(merchant.id, alien.id, "image/png") }
        verifyNoInteractions(storage)
    }

    @Test
    fun `rejects invalid colors blank names and logos outside current scope`() {
        assertFailsWith<IllegalArgumentException> { service.replace(merchant.id, null, BrandingSettings(primaryColor = "red")) }
        assertFailsWith<IllegalArgumentException> { service.replace(merchant.id, null, BrandingSettings(displayName = " ")) }
        assertFailsWith<IllegalArgumentException> { service.replace(merchant.id, branch.id, BrandingSettings(logoStorageKey = "merchants/${merchant.id}/logo.png")) }
        verify(merchants, never()).save(any())
        verify(branches, never()).save(any())
    }

    @Test
    fun `black white and midtone backgrounds receive readable foreground colors`() {
        assertEquals("#FFFFFF", MerchantBrandingService.textColor("#000000"))
        assertEquals("#000000", MerchantBrandingService.textColor("#FFFFFF"))
        assertEquals("#000000", MerchantBrandingService.textColor("#808080"))
    }

    @Test
    fun `uploads are unique and scoped and can be attached only to their scope`() {
        whenever(storage.presignUpload(any(), any(), any())).thenAnswer {
            PresignedUpload(it.arguments[0] as String, "https://upload.example", Instant.parse("2026-11-06T12:00:00Z"))
        }
        val first = service.createUploadUrl(merchant.id, branch.id, "image/png")
        val second = service.createUploadUrl(merchant.id, branch.id, "image/png")
        assertNotEquals(first.storageKey, second.storageKey)
        assertTrue(first.storageKey.startsWith("merchants/${merchant.id}/branches/${branch.id}/branding/"))
        service.replace(merchant.id, branch.id, BrandingSettings(logoStorageKey = first.storageKey))
        assertEquals(first.storageKey, branch.branding?.logoStorageKey)
        assertFailsWith<IllegalArgumentException> {
            service.replace(merchant.id, null, BrandingSettings(logoStorageKey = first.storageKey))
        }
        assertFailsWith<IllegalArgumentException> { service.createUploadUrl(merchant.id, null, "image/svg+xml") }
    }
}
