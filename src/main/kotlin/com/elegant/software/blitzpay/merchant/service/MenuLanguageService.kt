package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MenuLanguageResponse
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantSupportedMenuLanguage
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantMenuLanguageRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantSupportedMenuLanguageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

/**
 * Owns a Merchant's Original Menu Language and Supported Menu Languages, and decides which
 * translation language a catalog read is served in.
 */
@Service
@Transactional
class MenuLanguageService(
    private val menuLanguageRepository: MerchantMenuLanguageRepository,
    private val supportedMenuLanguageRepository: MerchantSupportedMenuLanguageRepository,
    private val offeringAssignmentRepository: MerchantOfferingAssignmentRepository,
    private val merchantRepository: MerchantRepository,
) {

    @Transactional(readOnly = true)
    fun catalogue(): List<MenuLanguageResponse> =
        menuLanguageRepository.findAllByActiveTrueOrderByCodeAsc()
            .map { MenuLanguageResponse(code = it.code, displayName = it.displayName) }

    @Transactional(readOnly = true)
    fun supportedLanguages(merchantId: UUID): Set<String> =
        supportedMenuLanguageRepository.findAllByMerchantId(merchantId).map { it.language }.toSortedSet()

    @Transactional(readOnly = true)
    fun originalLanguage(merchantId: UUID): String? =
        merchantRepository.findById(merchantId).map { it.originalMenuLanguage }.orElse(null)

    /** Applies requested language settings to [merchant]; null arguments keep the stored values. */
    fun applyMenuLanguages(merchant: Merchant, originalLanguage: String?, supportedLanguages: Set<String>?) {
        if (originalLanguage == null && supportedLanguages == null) return
        val supported = supportedLanguages?.map { normalize(it) }?.toSet() ?: supportedLanguages(merchant.id)
        val original = originalLanguage?.let { normalize(it) } ?: merchant.originalMenuLanguage

        val known = menuLanguageRepository.findAllByActiveTrueOrderByCodeAsc().map { it.code }.toSet()
        val unknown = (supported + listOfNotNull(original)) - known
        require(unknown.isEmpty()) { "Unknown menu languages: ${unknown.sorted().joinToString(",")}" }
        require(supported.isEmpty() || original != null) {
            "originalMenuLanguage must be set before adding supported menu languages"
        }
        require(original !in supported) {
            "originalMenuLanguage '$original' cannot also be a supported menu language"
        }

        merchant.originalMenuLanguage = original
        if (supportedLanguages != null) syncSupported(merchant.id, supported)
    }

    fun requireInternationalMenuReady(merchant: Merchant, offerings: Set<String>) {
        require(INTERNATIONAL_MENU !in offerings || merchant.originalMenuLanguage != null) {
            "$INTERNATIONAL_MENU requires originalMenuLanguage"
        }
    }

    fun deleteAllFor(merchantId: UUID) {
        supportedMenuLanguageRepository.deleteAllByMerchantId(merchantId)
    }

    /**
     * Returns the translation language to serve, or null to serve the Original Menu Language.
     *
     * An explicit [language] (admin and Printed Catalog reads) must be one of the merchant's languages
     * and ignores the International Menu offering. Otherwise [acceptLanguage] is honoured only while
     * the offering is enabled, matching on the primary subtag (`de-AT` → `de`).
     */
    @Transactional(readOnly = true)
    fun resolve(merchantId: UUID, language: String?, acceptLanguage: String?): String? {
        if (!language.isNullOrBlank()) {
            val requested = normalize(language)
            val original = originalLanguage(merchantId)
            if (requested == original) return null
            require(requested in supportedLanguages(merchantId)) {
                "Menu language '$requested' is not offered by merchant $merchantId"
            }
            return requested
        }
        if (acceptLanguage.isNullOrBlank()) return null
        if (!offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchantId, INTERNATIONAL_MENU)) return null

        val supported = supportedLanguages(merchantId)
        if (supported.isEmpty()) return null
        val original = originalLanguage(merchantId)
        for (range in parseAcceptLanguage(acceptLanguage)) {
            val primary = range.substringBefore('-')
            if (primary == original) return null
            if (primary in supported) return primary
        }
        return null
    }

    private fun syncSupported(merchantId: UUID, languages: Set<String>) {
        val existing = supportedMenuLanguageRepository.findAllByMerchantId(merchantId).associateBy { it.language }
        val toDelete = existing.keys - languages
        val toCreate = languages - existing.keys
        if (toDelete.isNotEmpty()) {
            supportedMenuLanguageRepository.deleteAll(existing.values.filter { it.language in toDelete })
        }
        if (toCreate.isNotEmpty()) {
            supportedMenuLanguageRepository.saveAll(toCreate.map { MerchantSupportedMenuLanguage(merchantId, it) })
        }
    }

    private fun parseAcceptLanguage(header: String): List<String> =
        runCatching { Locale.LanguageRange.parse(header) }
            .getOrDefault(emptyList())
            .filter { it.weight > 0.0 && it.range != "*" }
            .map { it.range.lowercase() }

    companion object {
        const val INTERNATIONAL_MENU = "INTERNATIONAL_MENU"

        fun normalize(language: String): String {
            val normalized = language.trim().lowercase()
            require(normalized.isNotEmpty()) { "menu language must not be blank" }
            return normalized
        }
    }
}
