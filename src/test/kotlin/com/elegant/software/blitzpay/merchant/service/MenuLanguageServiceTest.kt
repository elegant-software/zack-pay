package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMenuLanguage
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantSupportedMenuLanguage
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantMenuLanguageRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantSupportedMenuLanguageRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MenuLanguageServiceTest {

    private val menuLanguageRepository = mock<MerchantMenuLanguageRepository>()
    private val supportedRepository = mock<MerchantSupportedMenuLanguageRepository>()
    private val offeringAssignmentRepository = mock<MerchantOfferingAssignmentRepository>()
    private val merchantRepository = mock<MerchantRepository>()
    private val service = MenuLanguageService(
        menuLanguageRepository,
        supportedRepository,
        offeringAssignmentRepository,
        merchantRepository,
    )

    private val merchant = Merchant(merchantName = "Café Lena", businessType = "RESTAURANT", operatingCountry = "DE")
        .apply { originalMenuLanguage = "de" }

    @BeforeEach
    fun setUp() {
        whenever(menuLanguageRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(
            listOf("de", "en", "fr").map { MerchantMenuLanguage(it, it.uppercase()) }
        )
        whenever(merchantRepository.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(supportedRepository.findAllByMerchantId(merchant.id)).thenReturn(
            listOf(MerchantSupportedMenuLanguage(merchant.id, "en"))
        )
        whenever(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchant.id, "INTERNATIONAL_MENU"))
            .thenReturn(true)
    }

    @Test
    fun `catalogue exposes database metadata for menu and dashboard consumers`() {
        val entries = com.elegant.software.blitzpay.support.TestFixtureLoader.languageCatalogue()
        whenever(menuLanguageRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(entries)
        val catalogue = service.catalogue().associateBy { it.code }
        assertEquals("rtl", catalogue.getValue("ar").direction)
        assertEquals("SA", catalogue.getValue("ar").flagCode)
        assertEquals(true, catalogue.getValue("ar").adminUiEnabled)
        assertEquals(false, catalogue.getValue("fr").adminUiEnabled)
        assertEquals(true, catalogue.getValue("fr").menuEnabled)
    }

    @Test
    fun `menu settings reject a language enabled only for the dashboard`() {
        val entries = com.elegant.software.blitzpay.support.TestFixtureLoader.languageCatalogue()
        entries.first { it.code == "fr" }.menuEnabled = false
        whenever(menuLanguageRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(entries)
        assertFailsWith<IllegalArgumentException> {
            service.applyMenuLanguages(merchant, null, setOf("fr"))
        }
    }

    @Test
    fun `accept-language picks the first supported primary subtag`() {
        assertEquals("en", service.resolve(merchant.id, null, "fr-CH, en-GB;q=0.8, de;q=0.5"))
    }

    @Test
    fun `accept-language matching the original language serves the original`() {
        assertNull(service.resolve(merchant.id, null, "de-AT, en;q=0.5"))
    }

    @Test
    fun `accept-language is ignored while international menu is disabled`() {
        whenever(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchant.id, "INTERNATIONAL_MENU"))
            .thenReturn(false)

        assertNull(service.resolve(merchant.id, null, "en"))
    }

    @Test
    fun `no language and no header serves the original`() {
        assertNull(service.resolve(merchant.id, null, null))
    }

    @Test
    fun `explicit language ignores the offering but must be a merchant language`() {
        whenever(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchant.id, "INTERNATIONAL_MENU"))
            .thenReturn(false)

        assertEquals("en", service.resolve(merchant.id, "EN", null))
        assertNull(service.resolve(merchant.id, "de", null))
        assertFailsWith<IllegalArgumentException> { service.resolve(merchant.id, "fr", null) }
    }

    @Test
    fun `apply rejects the original language as a supported language`() {
        assertFailsWith<IllegalArgumentException> {
            service.applyMenuLanguages(merchant, null, setOf("de", "en"))
        }
    }

    @Test
    fun `apply rejects languages outside the catalogue`() {
        assertFailsWith<IllegalArgumentException> {
            service.applyMenuLanguages(merchant, null, setOf("xx"))
        }
    }

    @Test
    fun `apply requires an original language before supported languages`() {
        val noOriginal = Merchant(merchantName = "New", businessType = "RETAIL", operatingCountry = "US")

        assertFailsWith<IllegalArgumentException> {
            service.applyMenuLanguages(noOriginal, null, setOf("en"))
        }
    }

    @Test
    fun `apply syncs supported languages and keeps the original when omitted`() {
        service.applyMenuLanguages(merchant, null, setOf("fr"))

        assertEquals("de", merchant.originalMenuLanguage)
        val deleted = argumentCaptor<Iterable<MerchantSupportedMenuLanguage>>()
        verify(supportedRepository).deleteAll(deleted.capture())
        assertEquals(listOf("en"), deleted.firstValue.map { it.language })
        val saved = argumentCaptor<Iterable<MerchantSupportedMenuLanguage>>()
        verify(supportedRepository).saveAll(saved.capture())
        assertEquals(listOf("fr"), saved.firstValue.map { it.language })
    }

    @Test
    fun `apply with nothing requested changes nothing`() {
        service.applyMenuLanguages(merchant, null, null)

        verify(supportedRepository, never()).saveAll(any<Iterable<MerchantSupportedMenuLanguage>>())
    }

    @Test
    fun `international menu requires an original language`() {
        val noOriginal = Merchant(merchantName = "New", businessType = "RETAIL", operatingCountry = "US")

        assertFailsWith<IllegalArgumentException> {
            service.requireInternationalMenuReady(noOriginal, setOf("INTERNATIONAL_MENU"))
        }
        service.requireInternationalMenuReady(merchant, setOf("INTERNATIONAL_MENU"))
    }
}
