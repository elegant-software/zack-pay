package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.*
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantWhiteLabel
import com.elegant.software.blitzpay.merchant.persistence.repository.*
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class WhiteLabelConflictException : RuntimeException("White-label settings changed. Reload before saving.")

@Service
@Transactional(readOnly = true)
class WhiteLabelService(
    private val merchants: MerchantRepository,
    private val branches: MerchantBranchRepository,
    private val profiles: MerchantWhiteLabelRepository,
    private val assignments: MerchantOfferingAssignmentRepository,
    private val offerings: MerchantOfferingRepository,
    private val branding: MerchantBrandingService,
    private val storage: StorageService,
) {
    fun get(merchantId: UUID, branchId: UUID?, app: ThemeApp): WhiteLabelResponse {
        return resolve(merchantId, branchId, app, false)
    }

    private fun resolve(merchantId: UUID, branchId: UUID?, app: ThemeApp, ignoreLocal: Boolean): WhiteLabelResponse {
        val merchant = merchants.findById(merchantId).orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }
        branchId?.let {
            val branch = branches.findById(it).orElseThrow { NoSuchElementException("Branch not found: $it") }
            if (branch.merchantId != merchantId) throw NoSuchElementException("Branch not found: $it")
        }
        val parent = if (ignoreLocal && branchId == null) null else profiles.findByMerchantIdAndBranchIdAndApp(merchantId, null, app)
        val local = if (ignoreLocal) null else if (branchId == null) parent else profiles.findByMerchantIdAndBranchIdAndApp(merchantId, branchId, app)
        val p = parent?.settings ?: WhiteLabelSettings()
        val s = local?.settings ?: WhiteLabelSettings()
        // Legacy branding remains the migration fallback for shopper scope only.
        val legacy = if (app == ThemeApp.SHOPPER) branding.get(merchantId, branchId).effective else null
        val enabled = (p.enabled ?: legacy?.enabled ?: false) && (branchId == null || s.enabled != false)
        val light = if (enabled) s.light ?: p.light ?: ThemePalette(
            primary = legacy?.primaryColor ?: MerchantBrandingService.DEFAULT_PRIMARY,
            onPrimary = legacy?.primaryTextColor ?: "#000000",
            accent = legacy?.accentColor ?: MerchantBrandingService.DEFAULT_ACCENT,
            onAccent = legacy?.accentTextColor ?: "#FFFFFF") else ThemePalette()
        val dark = if (enabled) s.dark ?: p.dark ?: ThemePalette.dark() else ThemePalette.dark()
        val name = if (enabled) s.displayName ?: p.displayName ?: legacy?.displayName ?: merchant.merchantName else merchant.merchantName
        val logoKey = if (enabled) s.logoStorageKey ?: p.logoStorageKey else null
        val darkKey = if (enabled) s.darkLogoStorageKey ?: p.darkLogoStorageKey ?: logoKey else null
        val active = offerings.findAllByActiveTrueOrderByDisplayNameAsc().map { it.code }.toSet()
        val available = assignments.findAllByMerchantId(merchantId).map { it.offeringCode }.toSet().intersect(active)
        val hidden = p.hiddenFeatures + (if (branchId != null) s.hiddenFeatures else emptySet())
        return WhiteLabelResponse(s, local?.revision ?: -1, EffectiveWhiteLabel(
            enabled, name, if (enabled) s.title ?: p.title ?: name else name,
            logoKey?.let { storage.presignDownload(it) } ?: (if (enabled) legacy?.logoUrl else null)
                ?: merchant.logoStorageKey?.let { storage.presignDownload(it) },
            darkKey?.let { storage.presignDownload(it) },
            if (enabled) s.themeMode ?: p.themeMode ?: ThemeMode.SYSTEM else ThemeMode.SYSTEM,
            light, dark, available - hidden), available, local == null,
            s.logoStorageKey?.let { storage.presignDownload(it) }, s.darkLogoStorageKey?.let { storage.presignDownload(it) },
            if (ignoreLocal) null else resolve(merchantId, branchId, app, true).effective)
    }

    @Transactional
    fun replace(merchantId: UUID, branchId: UUID?, app: ThemeApp, update: WhiteLabelUpdate): WhiteLabelResponse {
        get(merchantId, branchId, app) // Validate tenant and branch before persisting.
        val s = update.settings
        listOf("displayName" to s.displayName, "title" to s.title).forEach { (field, value) ->
            require(value == null || (value.isNotBlank() && value.length <= 120)) { "$field must contain 1 to 120 characters" }
        }
        s.light?.let { ThemePaletteValidator.validate(it, "light") }
        s.dark?.let { ThemePaletteValidator.validate(it, "dark") }
        val codes = offerings.findAllByActiveTrueOrderByDisplayNameAsc().map { it.code }.toSet()
        require(s.hiddenFeatures.size <= 64 && codes.containsAll(s.hiddenFeatures)) { "hiddenFeatures must contain only active offering codes" }
        val prefix = "merchants/$merchantId/" + (branchId?.let { "branches/$it/" } ?: "") + "branding/"
        listOfNotNull(s.logoStorageKey, s.darkLogoStorageKey).forEach {
            require(Regex("${Regex.escape(prefix)}[0-9a-f-]{36}\\.(jpg|png|webp)").matches(it)) { "Logo must belong to this merchant and branch branding scope" }
        }
        val existing = profiles.findByMerchantIdAndBranchIdAndApp(merchantId, branchId, app)
        if ((existing?.revision ?: -1) != update.revision) throw WhiteLabelConflictException()
        val profile = existing ?: MerchantWhiteLabel(merchantId = merchantId, branchId = branchId, app = app)
        profile.settings = s.copy(displayName = s.displayName?.trim(), title = s.title?.trim())
        profiles.saveAndFlush(profile)
        return get(merchantId, branchId, app)
    }
}
