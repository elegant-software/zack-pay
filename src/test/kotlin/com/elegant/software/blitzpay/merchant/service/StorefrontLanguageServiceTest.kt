package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.support.TestFixtureLoader
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StorefrontLanguageServiceTest {
    @Test
    fun `ships English display names and native names from UTF8 message bundles`() {
        val service = StorefrontLanguageService()
        assertEquals(TestFixtureLoader.storefrontLanguages(), service.catalogue())
        assertEquals("Deutsch", service.catalogue().first { it.code == "de" }.nativeName)
        assertEquals("rtl", service.catalogue().first { it.code == "ar" }.direction)
    }
}
