package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.iam.web.KioskSettings
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKiosk
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantSupportedMenuLanguage
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskTokenRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantSupportedMenuLanguageRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KioskServiceTest {
    private val kiosks = mock<MerchantKioskRepository>()
    private val tokens = mock<MerchantKioskTokenRepository>()
    private val branches = mock<MerchantBranchRepository>()
    private val products = mock<MerchantProductRepository>()
    private val languages = mock<MerchantSupportedMenuLanguageRepository>()
    private val tokenService = KioskTokenService(tokens, kiosks)
    private val service = KioskService(kiosks, tokens, tokenService, branches, products, languages, KitchenPasswordGenerator())

    private val merchantId = UUID.randomUUID()
    private val branch = MerchantBranch(merchantId = merchantId, name = "Altstadt", branchCode = "BR-1A2B3C4D5E6F")
    private var stored: MerchantKiosk? = null

    init {
        whenever(kiosks.findByBranchId(branch.id)).thenAnswer { stored }
        whenever(kiosks.findByLogin(any())).thenAnswer { inv -> stored?.takeIf { it.login == inv.arguments[0] } }
        whenever(kiosks.save(any<MerchantKiosk>())).thenAnswer { stored = it.arguments[0] as MerchantKiosk; stored }
        whenever(branches.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(languages.findAllByMerchantId(merchantId)).thenReturn(
            listOf(MerchantSupportedMenuLanguage(merchantId, "en"), MerchantSupportedMenuLanguage(merchantId, "de")),
        )
    }

    private fun product(branchId: UUID?, active: Boolean = true) =
        MerchantProduct(merchantId = merchantId, name = "Cola", unitPrice = BigDecimal("2.50"), active = active, merchantBranchId = branchId)

    private fun stubProduct(p: MerchantProduct) {
        whenever(products.findByIdAndActiveTrueAndMerchantBranchId(p.id, branch.id)).thenReturn(
            if (p.active && p.merchantBranchId == branch.id) Optional.of(p) else Optional.empty(),
        )
    }

    private fun apiError(block: () -> Unit): ApiException = assertFailsWith<ApiException> { block() }

    @Test
    fun `enable returns a login and a password that is never stored in clear`() {
        val response = service.enable(branch)

        assertEquals("kiosk-br-1a2b3c4d5e6f", response.login)
        assertTrue(response.password.length >= 32)
        val kiosk = stored!!
        assertTrue(kiosk.enabled)
        assertNotEquals(response.password, kiosk.passwordHash)
        assertFalse(response.toString().contains(response.password))
    }

    @Test
    fun `reset issues a new password, keeps sessions and settings`() {
        val first = service.enable(branch)
        service.updateSettings(branch, KioskSettings(resetSeconds = 60))
        val second = service.enable(branch)

        assertNotEquals(first.password, second.password)
        assertEquals(60, stored!!.resetSeconds)
        verify(tokens, never()).deleteAllByBranchId(any())
        assertFailsWith<ApiException> { service.login(first.login, first.password) }
        assertNotNull(service.login(second.login, second.password))
    }

    @Test
    fun `disable turns the login off and revokes the branch's sessions`() {
        val login = service.enable(branch)
        val session = service.login(login.login, login.password)

        val status = service.disable(branch)

        assertFalse(status.enabled)
        assertEquals(login.login, status.login)
        verify(tokens).deleteAllByBranchId(branch.id)
        assertEquals(HttpStatus.UNAUTHORIZED, apiError { service.login(login.login, login.password) }.status)
        assertNotNull(session.token)
    }

    @Test
    fun `disable and status of a never enabled kiosk are empty with default settings`() {
        assertEquals(KioskSettings(), service.status(branch).settings)
        assertNull(service.status(branch).login)
        assertFalse(service.disable(branch).enabled)
        assertEquals(setOf("CARD", "CASH", "SCAN_TO_PAY"), service.findKioskPaymentChoices(branch.id))
    }

    @Test
    fun `login returns a token scoped to the branch and the merchant`() {
        val login = service.enable(branch)

        val session = service.login(login.login, login.password)

        assertEquals(branch.id, session.branchId)
        assertEquals(merchantId, session.merchantId)
        assertTrue(session.token.startsWith("kt_"))
    }

    @Test
    fun `login rejects a wrong password, an unknown login and a disabled login with the same error`() {
        val login = service.enable(branch)
        val wrong = apiError { service.login(login.login, "nope") }
        val unknown = apiError { service.login("kiosk-unknown", "nope") }
        service.disable(branch)
        val disabled = apiError { service.login(login.login, login.password) }

        listOf(wrong, unknown, disabled).forEach {
            assertEquals(HttpStatus.UNAUTHORIZED, it.status)
            assertEquals(KioskService.INVALID_KIOSK_CREDENTIALS, it.code)
        }
    }

    @Test
    fun `settings default to all three payment choices, 30 seconds, no languages, no advertised products`() {
        val settings = service.updateSettings(branch, KioskSettings())

        assertEquals(listOf("CARD", "CASH", "SCAN_TO_PAY"), settings.paymentChoices)
        assertEquals(30, settings.resetSeconds)
        assertEquals(emptyList(), settings.languages)
        assertEquals(emptyList(), settings.advertisedProductIds)
    }

    @Test
    fun `valid settings are saved and returned`() {
        val p = product(branch.id).also(::stubProduct)

        val saved = service.updateSettings(
            branch,
            KioskSettings(listOf("CASH", "CARD", "CASH"), 5, listOf("DE", "en"), listOf(p.id.toString())),
        )

        assertEquals(listOf("CASH", "CARD"), saved.paymentChoices)
        assertEquals(5, saved.resetSeconds)
        assertEquals(listOf("de", "en"), saved.languages)
        assertEquals(listOf(p.id.toString()), service.settings(branch.id).advertisedProductIds)
        assertEquals(setOf("CASH", "CARD"), service.findKioskPaymentChoices(branch.id))
    }

    @Test
    fun `settings reject unknown or empty payment choices`() {
        apiError { service.updateSettings(branch, KioskSettings(paymentChoices = listOf("CARD", "BITCOIN"))) }.also {
            assertEquals(HttpStatus.BAD_REQUEST, it.status)
            assertEquals(KioskService.UNKNOWN_PAYMENT_CHOICE, it.code)
        }
        apiError { service.updateSettings(branch, KioskSettings(paymentChoices = emptyList())) }.also {
            assertEquals(HttpStatus.BAD_REQUEST, it.status)
            assertEquals(KioskService.PAYMENT_CHOICES_REQUIRED, it.code)
        }
    }

    @Test
    fun `settings reject reset seconds outside 5 to 300`() {
        listOf(4, 301, 0, -1).forEach { seconds ->
            apiError { service.updateSettings(branch, KioskSettings(resetSeconds = seconds)) }.also {
                assertEquals(HttpStatus.BAD_REQUEST, it.status)
                assertEquals(KioskService.RESET_SECONDS_OUT_OF_RANGE, it.code)
            }
        }
        listOf(5, 300).forEach { assertEquals(it, service.updateSettings(branch, KioskSettings(resetSeconds = it)).resetSeconds) }
    }

    @Test
    fun `settings reject languages the merchant does not support`() {
        apiError { service.updateSettings(branch, KioskSettings(languages = listOf("en", "fr"))) }.also {
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, it.status)
            assertEquals(KioskService.LANGUAGE_NOT_SUPPORTED, it.code)
        }
    }

    @Test
    fun `settings reject advertised products of another branch, inactive or unknown ones`() {
        val other = product(UUID.randomUUID()).also(::stubProduct)
        val inactive = product(branch.id, active = false).also(::stubProduct)

        listOf(other.id.toString(), inactive.id.toString(), UUID.randomUUID().toString()).forEach { id ->
            apiError { service.updateSettings(branch, KioskSettings(advertisedProductIds = listOf(id))) }.also {
                assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, it.status)
                assertEquals(KioskService.ADVERTISED_PRODUCT_INVALID, it.code)
            }
        }
        apiError { service.updateSettings(branch, KioskSettings(advertisedProductIds = listOf("not-a-uuid"))) }.also {
            assertEquals(HttpStatus.BAD_REQUEST, it.status)
        }
    }

    @Test
    fun `an invalid update leaves the stored settings untouched`() {
        service.updateSettings(branch, KioskSettings(resetSeconds = 99))
        apiError { service.updateSettings(branch, KioskSettings(resetSeconds = 1)) }
        assertEquals(99, service.settings(branch.id).resetSeconds)
    }
}
