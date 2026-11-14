package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.api.KioskGateway
import com.elegant.software.blitzpay.merchant.iam.config.KioskAccessProperties
import com.elegant.software.blitzpay.merchant.iam.web.KioskSettings
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKioskSettings
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskSettingsRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantSupportedMenuLanguageRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Kiosk Settings of a Branch, shared by all its Kiosks. The Kiosk Logins themselves are in [KioskAccessService]. */
@Service
class KioskService(
    private val kiosks: MerchantKioskRepository,
    private val settingsRepository: MerchantKioskSettingsRepository,
    private val products: MerchantProductRepository,
    private val supportedLanguages: MerchantSupportedMenuLanguageRepository,
    @Value("\${keycloak.iam.kiosk.client-id:${KioskAccessProperties.KIOSK_CLIENT_ID}}") private val kioskClientId: String,
) : KioskGateway {
    private val pinEncoder = BCryptPasswordEncoder()

    // ponytail: in-memory and per pod, so N pods allow N x MAX_PIN_FAILURES guesses; move to the DB or Redis if that matters.
    private val pinFailures = ConcurrentHashMap<UUID, Pair<Int, Instant>>()

    @Transactional(readOnly = true)
    fun settings(branchId: UUID): KioskSettings = settingsRepository.findByBranchId(branchId)?.toSettings() ?: DEFAULT_SETTINGS

    override fun findKioskPaymentChoices(branchId: UUID): Set<String> = settings(branchId).paymentChoices.toSet()

    override fun isKioskToken(jwt: Jwt?): Boolean = jwt?.getClaimAsString(AUTHORIZED_PARTY) == kioskClientId

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

        val settings = settingsRepository.findByBranchId(branch.id) ?: MerchantKioskSettings(branchId = branch.id)
        settings.paymentChoices = paymentChoices
        settings.resetSeconds = request.resetSeconds
        settings.languages = languages
        settings.advertisedProductIds = productIds
        when {
            request.adminPin == null -> Unit
            request.adminPin.isEmpty() -> settings.adminPinHash = null
            ADMIN_PIN.matches(request.adminPin) -> settings.adminPinHash = pinEncoder.encode(request.adminPin)
            else -> bad("adminPin must be 4 to 8 digits", ADMIN_PIN_INVALID)
        }
        settings.updatedAt = Instant.now()
        return settingsRepository.save(settings).toSettings()
    }

    /**
     * Checks a Kiosk's Admin PIN. After [MAX_PIN_FAILURES] wrong PINs in a row the Branch is locked out until
     * [PIN_LOCKOUT] has passed since the last one, so a 4-digit PIN can't be guessed at the counter.
     */
    fun unlockAdmin(branchId: UUID, pin: String) {
        val hash = settingsRepository.findByBranchId(branchId)?.adminPinHash
            ?: throw ApiException(HttpStatus.CONFLICT, "No Admin PIN is set for this Branch", ADMIN_PIN_NOT_SET)
        val now = Instant.now()
        val failures = pinFailures[branchId]
        if (failures != null && failures.first >= MAX_PIN_FAILURES && failures.second.plus(PIN_LOCKOUT) > now) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong Admin PINs; try again later", ADMIN_PIN_LOCKED)
        }
        if (pinEncoder.matches(pin, hash)) {
            pinFailures.remove(branchId)
            return
        }
        pinFailures.compute(branchId) { _, prev ->
            (if (prev == null || prev.second.plus(PIN_LOCKOUT) <= now) 1 else prev.first + 1) to now
        }
        throw ApiException(HttpStatus.FORBIDDEN, "Wrong Admin PIN", ADMIN_PIN_WRONG)
    }

    /** Merchant deletion: the Kiosks and Kiosk Settings of the Branches go first (no cascading deletes). */
    @Transactional
    fun deleteAllForBranches(branchIds: Collection<UUID>) {
        if (branchIds.isEmpty()) return
        // ponytail: the Kiosks' Keycloak users stay, like the Kitchen Login's; deleting the Branch group ends their access
        kiosks.deleteAllByBranchIdIn(branchIds)
        settingsRepository.deleteAllByBranchIdIn(branchIds)
    }

    private fun MerchantKioskSettings.toSettings() =
        KioskSettings(paymentChoices, resetSeconds, languages, advertisedProductIds, adminPinSet = adminPinHash != null)

    private fun bad(message: String, code: String): Nothing = throw ApiException(HttpStatus.BAD_REQUEST, message, code)
    private fun unprocessable(message: String, code: String): Nothing = throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message, code)

    companion object {
        const val PAYMENT_CHOICES_REQUIRED = "KIOSK_PAYMENT_CHOICES_REQUIRED"
        const val UNKNOWN_PAYMENT_CHOICE = "UNKNOWN_KIOSK_PAYMENT_CHOICE"
        const val RESET_SECONDS_OUT_OF_RANGE = "KIOSK_RESET_SECONDS_OUT_OF_RANGE"
        const val LANGUAGE_NOT_SUPPORTED = "KIOSK_LANGUAGE_NOT_SUPPORTED"
        const val ADVERTISED_PRODUCT_INVALID = "KIOSK_ADVERTISED_PRODUCT_INVALID"
        const val ADMIN_PIN_INVALID = "KIOSK_ADMIN_PIN_INVALID"
        const val ADMIN_PIN_NOT_SET = "KIOSK_ADMIN_PIN_NOT_SET"
        const val ADMIN_PIN_WRONG = "KIOSK_ADMIN_PIN_WRONG"
        const val ADMIN_PIN_LOCKED = "KIOSK_ADMIN_PIN_LOCKED"
        const val MAX_PIN_FAILURES = 5
        val PIN_LOCKOUT: java.time.Duration = java.time.Duration.ofMinutes(5)
        private val ADMIN_PIN = Regex("\\d{4,8}")

        /** OIDC "authorized party": the client the token was issued to. */
        private const val AUTHORIZED_PARTY = "azp"
        private val PAYMENT_CHOICES = MerchantKioskSettings.DEFAULT_PAYMENT_CHOICES.toSet()
        private val RESET_SECONDS_RANGE = 5..300
        private val DEFAULT_SETTINGS = KioskSettings()
    }
}
