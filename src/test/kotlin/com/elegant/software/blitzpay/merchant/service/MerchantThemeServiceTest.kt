package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.ThemeApp
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTheme
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantThemeRepository
import com.elegant.software.blitzpay.merchant.support.MerchantThemeFixtureLoader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.UUID
import kotlin.test.*

class MerchantThemeServiceTest {
    private val merchants = mock<MerchantRepository>()
    private val themes = mock<MerchantThemeRepository>()
    private val service = MerchantThemeService(merchants, themes)
    private val merchantId = UUID.randomUUID()
    private val request = MerchantThemeFixtureLoader.request()

    @BeforeEach
    fun setup() {
        whenever(merchants.existsById(merchantId)).thenReturn(true)
        whenever(themes.save(any<MerchantTheme>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `merchant can save multiple palettes with readable foreground colors`() {
        val first = service.create(merchantId, request)
        val second = service.create(merchantId, request.copy(name = "Winter"))
        assertNotEquals(first.id, second.id)
        assertEquals("#123ABC", first.primaryColor)
        assertEquals("#FFFFFF", first.primaryTextColor)
        assertEquals("#000000", first.accentTextColor)
        assertEquals(merchantId, second.merchantId)
    }

    @Test
    fun `list and update stay within the requested merchant`() {
        val theme = MerchantTheme(merchantId = merchantId, name = request.name,
            primaryColor = request.primaryColor, accentColor = request.accentColor)
        whenever(themes.findAllByMerchantIdAndAppOrderByNameAscIdAsc(merchantId, ThemeApp.SHOPPER)).thenReturn(listOf(theme))
        whenever(themes.findByIdAndMerchantIdAndApp(theme.id, merchantId, ThemeApp.SHOPPER)).thenReturn(theme)
        assertEquals(theme.id, service.list(merchantId).single().id)
        assertEquals("Winter", service.update(merchantId, theme.id, request.copy(name = " Winter ")).name)
        assertFailsWith<NoSuchElementException> { service.update(merchantId, UUID.randomUUID(), request) }
    }

    @Test
    fun `merchant and shopper app palettes are isolated within the same merchant`() {
        val shopper = service.create(merchantId, request)
        val merchant = service.create(merchantId, request, ThemeApp.MERCHANT)
        assertEquals(ThemeApp.SHOPPER, shopper.app)
        assertEquals(ThemeApp.MERCHANT, merchant.app)
        val theme = MerchantTheme(merchantId = merchantId, name = request.name,
            primaryColor = request.primaryColor, accentColor = request.accentColor, app = ThemeApp.MERCHANT)
        whenever(themes.findAllByMerchantIdAndAppOrderByNameAscIdAsc(merchantId, ThemeApp.MERCHANT)).thenReturn(listOf(theme))
        whenever(themes.findByIdAndMerchantIdAndApp(theme.id, merchantId, ThemeApp.MERCHANT)).thenReturn(theme)
        assertEquals(ThemeApp.MERCHANT, service.list(merchantId, ThemeApp.MERCHANT).single().app)
        assertFailsWith<NoSuchElementException> { service.update(merchantId, theme.id, request) }
        assertEquals("Winter", service.update(merchantId, theme.id, request.copy(name = "Winter"), ThemeApp.MERCHANT).name)
    }

    @Test
    fun `invalid palettes and nonexistent merchants are rejected before saving`() {
        listOf(request.copy(name = " "), request.copy(name = "a".repeat(121)),
            request.copy(primaryColor = "red"), request.copy(accentColor = "#FFF")).forEach {
            assertFailsWith<IllegalArgumentException> { service.create(merchantId, it) }
        }
        assertFailsWith<NoSuchElementException> { service.create(UUID.randomUUID(), request) }
        assertFailsWith<NoSuchElementException> { service.list(UUID.randomUUID()) }
        verify(themes, never()).save(any())
    }
}
