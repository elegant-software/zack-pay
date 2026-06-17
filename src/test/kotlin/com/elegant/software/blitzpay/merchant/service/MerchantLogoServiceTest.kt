package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.BusinessProfile
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.PrimaryContact
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.storage.PresignedUpload
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MerchantLogoServiceTest {
    private val repository = mock<MerchantApplicationRepository>()
    private val storageService = mock<StorageService>()
    private val service = MerchantLogoService(repository, storageService)

    @Test
    fun `create upload url returns canonical merchant logo key`() {
        val merchant = MerchantApplication(
            applicationReference = "BLTZ-LOGO",
            businessProfile = BusinessProfile(
                legalBusinessName = "Logo GmbH",
                businessType = "RETAIL",
                registrationNumber = "DE-LOGO",
                operatingCountry = "DE",
                primaryBusinessAddress = "Logo Strasse 1"
            ),
            primaryContact = PrimaryContact("Jane Doe", "jane@example.com", "+491234")
        )
        whenever(repository.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(storageService.presignUpload(any(), any(), any())).thenReturn(
            PresignedUpload(
                storageKey = "merchants/${merchant.id}/logo.webp",
                uploadUrl = "https://upload.example/logo.webp",
                expiresAt = Instant.parse("2026-04-24T18:30:00Z")
            )
        )

        val response = service.createUploadUrl(merchant.id, "image/webp")

        assertEquals("merchants/${merchant.id}/logo.webp", response.storageKey)
        assertEquals("https://upload.example/logo.webp", response.uploadUrl)
    }

    @Test
    fun `attach logo rejects unexpected storage key`() {
        val merchantId = java.util.UUID.randomUUID()

        val ex = assertFailsWith<IllegalArgumentException> {
            service.attachLogo(merchantId, "merchants/$merchantId/branches/branch/logo.webp")
        }

        assertEquals(
            "Merchant logo storageKey must match merchants/$merchantId/logo.{jpg|png|webp}",
            ex.message
        )
    }
}
