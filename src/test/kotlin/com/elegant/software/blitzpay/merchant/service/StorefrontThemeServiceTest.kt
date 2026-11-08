package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.SaveStorefrontThemeRequest
import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontTheme
import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontThemeStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.StorefrontThemeRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorefrontThemeServiceTest {
    private val repository = mock<StorefrontThemeRepository>().also {
        whenever(it.findById(any())).thenReturn(Optional.empty())
        whenever(it.save(any<StorefrontTheme>())).thenAnswer { inv -> inv.arguments[0] }
    }
    private val service = StorefrontThemeService(repository)

    private fun save(tokens: Map<String, Map<String, String>>, code: String = "OCEAN") =
        service.save(code, SaveStorefrontThemeRequest(displayName = "Ocean", tokens = tokens))

    @Test
    fun `saves a draft with lower-cased colours and no warnings when readable`() {
        val saved = save(mapOf("light" to mapOf("ink" to "#12232A", "surface" to "#FFFFFF", "action" to "#087f68")))
        assertEquals("#12232a", saved.tokens["light"]!!["ink"])
        assertEquals(StorefrontThemeStatus.DRAFT, saved.status)
        assertTrue(saved.warnings.isEmpty())
    }

    @Test
    fun `rejects anything that is not a known colour variable with a hex value`() {
        assertThrows<IllegalArgumentException> { save(mapOf("light" to mapOf("background-image" to "#ffffff"))) }
        assertThrows<IllegalArgumentException> { save(mapOf("light" to mapOf("ink" to "red; display:none"))) }
        assertThrows<IllegalArgumentException> { save(mapOf("sepia" to mapOf("ink" to "#000000"))) }
        assertThrows<IllegalArgumentException> { save(emptyMap(), code = "bad code") }
        assertThrows<IllegalArgumentException> { save(emptyMap(), code = "DEFAULT") }
    }

    @Test
    fun `warns but still saves when text is hard to read`() {
        val saved = save(mapOf("dark" to mapOf("ink" to "#333333", "bg" to "#222222")))
        assertEquals(1, saved.warnings.size)
        assertTrue(saved.warnings.single().startsWith("dark: ink on bg"))
    }

    @Test
    fun `published themes cannot be changed in place`() {
        whenever(repository.findById("HERITAGE")).thenReturn(
            Optional.of(StorefrontTheme("HERITAGE", "Heritage", status = StorefrontThemeStatus.PUBLISHED))
        )
        assertThrows<IllegalArgumentException> { save(emptyMap(), code = "HERITAGE") }
    }

    @Test
    fun `merchants can only pick published themes`() {
        whenever(repository.findById("OCEAN")).thenReturn(Optional.of(StorefrontTheme("OCEAN", "Ocean")))
        assertThrows<IllegalArgumentException> { service.requireSelectable("OCEAN") }
    }

    @Test
    fun `contrast matches WCAG`() {
        assertEquals(21.0, StorefrontThemeService.contrast("#000000", "#ffffff"), 0.01)
    }
}
