package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.merchant.iam.service.SignupService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals

class LegalControllerTest {
    private val legal = LegalProperties(termsVersion = "2026-11-08", apiBaseUrl = "https://api.example.test/")
    private val controller = LegalController(legal)

    @Test
    fun `links point to the requested language and fall back to English`() {
        val de = controller.info("DE")
        assertEquals("de", de.language)
        assertEquals("2026-11-08", de.termsVersion)
        assertEquals("https://api.example.test/v1/legal/de/terms", de.termsUrl)
        assertEquals("https://api.example.test/v1/legal/de/privacy", de.privacyUrl)

        assertEquals("en", controller.info("fr").language)
        assertEquals("en", controller.info(null).language)
        assertEquals("en", controller.info("../de").language)
    }

    @Test
    fun `serves known documents only`() {
        assertEquals(HttpStatus.OK, controller.document("de", "privacy").statusCode)
        assertEquals(HttpStatus.OK, controller.document("en", "terms").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.document("de", "secret").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.document("..", "terms").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.document("fr", "terms").statusCode)
    }

    @Test
    fun `signup accepts only the current terms version`() {
        val service = mock<SignupService> {
            on { signupShopper(any(), any(), any(), anyOrNull()) } doReturn UUID.randomUUID()
            on { signupMerchant(any(), any(), any(), any(), any(), any()) } doReturn UUID.randomUUID()
        }
        val signup = SignupController(service, legal)
        val merchant = MerchantSignupRequest("Ann Lee", "Ann's Cafe", "ann@example.test", "Secret123", "CAFE", "2026-01-01")

        assertThrows<TermsOutdatedException> { signup.merchant(merchant) }
        assertThrows<TermsOutdatedException> {
            signup.shopper(ShopperSignupRequest("Bo", "bo@example.test", "Secret123", acceptedTermsVersion = "old"))
        }
        verify(service, never()).signupMerchant(any(), any(), any(), any(), any(), any())

        signup.shopper(ShopperSignupRequest("Bo", "bo@example.test", "Secret123"))
        verify(service).signupShopper(eq("Bo"), eq("bo@example.test"), eq("Secret123"), anyOrNull())
        signup.merchant(merchant.copy(acceptedTermsVersion = "2026-11-08"))
        verify(service).signupMerchant(any(), any(), any(), any(), any(), eq("2026-11-08"))
    }
}
