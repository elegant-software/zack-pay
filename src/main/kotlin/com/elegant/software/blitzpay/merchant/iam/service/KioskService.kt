package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.api.KioskSettingsGateway
import com.elegant.software.blitzpay.merchant.iam.web.KioskLoginResponse
import com.elegant.software.blitzpay.merchant.iam.web.KioskSessionResponse
import com.elegant.software.blitzpay.merchant.iam.web.KioskSettings
import com.elegant.software.blitzpay.merchant.iam.web.KioskStatusResponse
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKiosk
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskTokenRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantSupportedMenuLanguageRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * The Kiosk of a Branch: Kiosk Login lifecycle (modelled on the Kitchen Login: one shared login per Branch, password
 * shown once) and Kiosk Settings. The password is never stored or logged; only its BCrypt hash is kept.
 */
@Service
class KioskService(
    private val kiosks: MerchantKioskRepository,
    private val tokens: MerchantKioskTokenRepository,
    private val tokenService: KioskTokenService,
    private val branches: MerchantBranchRepository,
    private val products: MerchantProductRepository,
    private val supportedLanguages: MerchantSupportedMenuLanguageRepository,
    private val passwordGenerator: KitchenPasswordGenerator,
) : KioskSettingsGateway {
    private val log = LoggerFactory.getLogger(KioskService::class.java)
    private val encoder = BCryptPasswordEncoder()

    /** Enables the Kiosk Login, or resets it: a new password, shown once. Sessions already signed in keep working. */
    @Transactional
    fun enable(branch: MerchantBranch): KioskLoginResponse {
        val kiosk = kiosks.findByBranchId(branch.id) ?: MerchantKiosk(branchId = branch.id)
        val login = kioskLogin(branch.branchCode)
        val password = passwordGenerator.generate()
        kiosk.login = login
        kiosk.passwordHash = encoder.encode(password)
        kiosk.enabled = true
        kiosk.updatedAt = Instant.now()
        kiosks.save(kiosk)
        log.info("kiosk login provisioned branchId={} login={}", branch.id, login)
        return KioskLoginResponse(login, password)
    }

    /** Disables the Kiosk Login and revokes every Kiosk session of the Branch. */
    @Transactional
    fun disable(branch: MerchantBranch): KioskStatusResponse {
        val kiosk = kiosks.findByBranchId(branch.id) ?: return KioskStatusResponse(false, null, DEFAULT_SETTINGS)
        kiosk.enabled = false
        kiosk.updatedAt = Instant.now()
        kiosks.save(kiosk)
        tokens.deleteAllByBranchId(branch.id)
        log.info("kiosk login disabled branchId={}", branch.id)
        return KioskStatusResponse(false, kiosk.login, kiosk.toSettings())
    }

    @Transactional(readOnly = true)
    fun status(branch: MerchantBranch): KioskStatusResponse {
        val kiosk = kiosks.findByBranchId(branch.id) ?: return KioskStatusResponse(false, null, DEFAULT_SETTINGS)
        return KioskStatusResponse(kiosk.enabled, kiosk.login, kiosk.toSettings())
    }

    @Transactional(readOnly = true)
    fun settings(branchId: UUID): KioskSettings = kiosks.findByBranchId(branchId)?.toSettings() ?: DEFAULT_SETTINGS

    override fun findKioskPaymentChoices(branchId: UUID): Set<String> = settings(branchId).paymentChoices.toSet()

    @Transactional
    fun updateSettings(branch: MerchantBranch, request: KioskSettings): KioskSettings {
        val paymentChoices = request.paymentChoices.distinct()
        if (paymentChoices.isEmpty()) bad("paymentChoices must not be empty", PAYMENT_CHOICES_REQUIRED)
        paymentChoices.firstOrNull { it !in PAYMENT_CHOICES }
            ?.let { bad("Unknown payment choice: $it", UNKNOWN_PAYMENT_CHOICE) }
        if (request.resetSeconds !in RESET_SECONDS_RANGE) {
            bad("resetSeconds must be between ${RESET_SECONDS_RANGE.first} and ${RESET_SECONDS_RANGE.last}", RESET_SECONDS_OUT_OF_RANGE)
        }

        val supported = supportedLanguages.findAllByMerchantId(branch.merchantId).map { it.language }
        val languages = request.languages.distinct().map { requested ->
            supported.firstOrNull { it.equals(requested, ignoreCase = true) }
                ?: unprocessable("Language is not a supported menu language of this Merchant: $requested", LANGUAGE_NOT_SUPPORTED)
        }.distinct()

        val productIds = request.advertisedProductIds.distinct().map { raw ->
            val id = runCatching { UUID.fromString(raw) }.getOrNull()
                ?: bad("Not a product id: $raw", ADVERTISED_PRODUCT_INVALID)
            if (!products.findByIdAndActiveTrueAndMerchantBranchId(id, branch.id).isPresent) {
                unprocessable("Product is not an active product of this Branch: $raw", ADVERTISED_PRODUCT_INVALID)
            }
            id.toString()
        }

        val kiosk = kiosks.findByBranchId(branch.id) ?: MerchantKiosk(branchId = branch.id)
        kiosk.paymentChoices = paymentChoices
        kiosk.resetSeconds = request.resetSeconds
        kiosk.languages = languages
        kiosk.advertisedProductIds = productIds
        kiosk.updatedAt = Instant.now()
        return kiosks.save(kiosk).toSettings()
    }

    /** Signs a Kiosk in with the shared Kiosk Login; the token is scoped to the login's Branch. */
    @Transactional
    fun login(login: String, password: String): KioskSessionResponse {
        val kiosk = kiosks.findByLogin(login)?.takeIf { it.enabled }
        val matches = encoder.matches(password, kiosk?.passwordHash ?: DUMMY_HASH)
        if (kiosk == null || !matches) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Invalid Kiosk Login", INVALID_KIOSK_CREDENTIALS)
        }
        val branch = branches.findById(kiosk.branchId).orElseThrow { NoSuchElementException("Branch not found: ${kiosk.branchId}") }
        return KioskSessionResponse(tokenService.issue(branch.merchantId, branch.id), branch.merchantId, branch.id)
    }

    /** Merchant deletion: the Kiosks and sessions of the Branches go first (no cascading deletes). */
    @Transactional
    fun deleteAllForBranches(branchIds: Collection<UUID>) {
        if (branchIds.isEmpty()) return
        tokens.deleteAllByBranchIdIn(branchIds)
        kiosks.deleteAllByBranchIdIn(branchIds)
    }

    private fun MerchantKiosk.toSettings() = KioskSettings(paymentChoices, resetSeconds, languages, advertisedProductIds)

    private fun bad(message: String, code: String): Nothing = throw ApiException(HttpStatus.BAD_REQUEST, message, code)
    private fun unprocessable(message: String, code: String): Nothing = throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message, code)

    companion object {
        const val PAYMENT_CHOICES_REQUIRED = "KIOSK_PAYMENT_CHOICES_REQUIRED"
        const val UNKNOWN_PAYMENT_CHOICE = "UNKNOWN_KIOSK_PAYMENT_CHOICE"
        const val RESET_SECONDS_OUT_OF_RANGE = "KIOSK_RESET_SECONDS_OUT_OF_RANGE"
        const val LANGUAGE_NOT_SUPPORTED = "KIOSK_LANGUAGE_NOT_SUPPORTED"
        const val ADVERTISED_PRODUCT_INVALID = "KIOSK_ADVERTISED_PRODUCT_INVALID"
        const val INVALID_KIOSK_CREDENTIALS = "INVALID_KIOSK_CREDENTIALS"

        private val PAYMENT_CHOICES = MerchantKiosk.DEFAULT_PAYMENT_CHOICES.toSet()
        private val RESET_SECONDS_RANGE = 5..300
        private val DEFAULT_SETTINGS = KioskSettings()
        private val NON_LOGIN_CHARS = Regex("[^a-z0-9._-]+")

        /** Keeps login time the same for unknown logins, so response time doesn't reveal which logins exist. */
        private val DUMMY_HASH = BCryptPasswordEncoder().encode("not-a-kiosk-password")

        /** Human-readable login from the Branch Code (ADR-0012), e.g. `BR-1A2B3C` -> `kiosk-br-1a2b3c`. */
        fun kioskLogin(branchCode: String): String = "kiosk-" + branchCode.lowercase().replace(NON_LOGIN_CHARS, "-").trim('-')
    }
}
