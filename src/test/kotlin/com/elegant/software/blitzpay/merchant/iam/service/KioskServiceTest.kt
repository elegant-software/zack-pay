package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.iam.web.KioskSettings
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKioskSettings
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantSupportedMenuLanguage
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskSettingsRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantSupportedMenuLanguageRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KioskServiceTest {
    private val settingsRepository = mock<MerchantKioskSettingsRepository>()
    private val products = mock<MerchantProductRepository>()
    private val languages = mock<MerchantSupportedMenuLanguageRepository>()
    private val service = KioskService(mock<MerchantKioskRepository>(), settingsRepository, products, languages, "blitzpay-kiosk")

    private val merchantId = UUID.randomUUID()
    private val branch = MerchantBranch(merchantId = merchantId, name = "Altstadt", branchCode = "BR-1A2B3C4D5E6F")
    private var stored: MerchantKioskSettings? = null

    init {
        whenever(settingsRepository.findByBranchId(branch.id)).thenAnswer { stored }
        whenever(settingsRepository.save(any<MerchantKioskSettings>())).thenAnswer { stored = it.arguments[0] as MerchantKioskSettings; stored }
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
    fun `a branch without settings gets the defaults`() {
        assertEquals(KioskSettings(), service.settings(branch.id))
        assertEquals(setOf("CARD", "CASH", "SCAN_TO_PAY"), service.findKioskPaymentChoices(branch.id))
    }

    @Test
    fun `only tokens issued to the kiosk client are kiosk tokens`() {
        fun jwt(azp: String?) = Jwt.withTokenValue("t").header("alg", "none").subject("u")
            .apply { if (azp != null) claim("azp", azp) }.build()

        assertTrue(service.isKioskToken(jwt("blitzpay-kiosk")))
        assertFalse(service.isKioskToken(jwt("blitzpay-kitchen")))
        assertFalse(service.isKioskToken(jwt(null)))
        assertFalse(service.isKioskToken(null))
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

    @Test
    fun `the admin pin is stored hashed, kept when absent and removed when empty`() {
        service.updateSettings(branch, KioskSettings(adminPin = "4821"))
        val hash = stored!!.adminPinHash!!
        assertFalse(hash.contains("4821"))
        assertTrue(service.settings(branch.id).adminPinSet)

        service.updateSettings(branch, KioskSettings())
        assertEquals(hash, stored!!.adminPinHash)

        service.updateSettings(branch, KioskSettings(adminPin = ""))
        assertEquals(null, stored!!.adminPinHash)
    }

    @Test
    fun `too many wrong admin pins lock the branch out, even for the right pin`() {
        service.updateSettings(branch, KioskSettings(adminPin = "4821"))
        service.unlockAdmin(branch.id, "4821")

        repeat(KioskService.MAX_PIN_FAILURES) {
            assertEquals(KioskService.ADMIN_PIN_WRONG, apiError { service.unlockAdmin(branch.id, "0000") }.code)
        }
        val locked = apiError { service.unlockAdmin(branch.id, "4821") }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.status)
    }
}
