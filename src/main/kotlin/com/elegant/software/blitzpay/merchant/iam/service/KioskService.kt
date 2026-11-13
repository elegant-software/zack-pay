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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Kiosk Settings of a Branch, shared by all its Kiosks. The Kiosk Logins themselves are in [KioskAccessService]. */
@Service
class KioskService(
    private val kiosks: MerchantKioskRepository,
    private val settingsRepository: MerchantKioskSettingsRepository,
    private val products: MerchantProductRepository,
    private val supportedLanguages: MerchantSupportedMenuLanguageRepository,
    @Value("\${keycloak.iam.kiosk.client-id:${KioskAccessProperties.KIOSK_CLIENT_ID}}") private val kioskClientId: String,
) : KioskGateway {

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
        settings.updatedAt = Instant.now()
        return settingsRepository.save(settings).toSettings()
    }

    /** Merchant deletion: the Kiosks and Kiosk Settings of the Branches go first (no cascading deletes). */
    @Transactional
    fun deleteAllForBranches(branchIds: Collection<UUID>) {
        if (branchIds.isEmpty()) return
        // ponytail: the Kiosks' Keycloak users stay, like the Kitchen Login's; deleting the Branch group ends their access
        kiosks.deleteAllByBranchIdIn(branchIds)
        settingsRepository.deleteAllByBranchIdIn(branchIds)
    }

    private fun MerchantKioskSettings.toSettings() = KioskSettings(paymentChoices, resetSeconds, languages, advertisedProductIds)

    private fun bad(message: String, code: String): Nothing = throw ApiException(HttpStatus.BAD_REQUEST, message, code)
    private fun unprocessable(message: String, code: String): Nothing = throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message, code)

    companion object {
        const val PAYMENT_CHOICES_REQUIRED = "KIOSK_PAYMENT_CHOICES_REQUIRED"
        const val UNKNOWN_PAYMENT_CHOICE = "UNKNOWN_KIOSK_PAYMENT_CHOICE"
        const val RESET_SECONDS_OUT_OF_RANGE = "KIOSK_RESET_SECONDS_OUT_OF_RANGE"
        const val LANGUAGE_NOT_SUPPORTED = "KIOSK_LANGUAGE_NOT_SUPPORTED"
        const val ADVERTISED_PRODUCT_INVALID = "KIOSK_ADVERTISED_PRODUCT_INVALID"

        /** OIDC "authorized party": the client the token was issued to. */
        private const val AUTHORIZED_PARTY = "azp"
        private val PAYMENT_CHOICES = MerchantKioskSettings.DEFAULT_PAYMENT_CHOICES.toSet()
        private val RESET_SECONDS_RANGE = 5..300
        private val DEFAULT_SETTINGS = KioskSettings()
    }
}
