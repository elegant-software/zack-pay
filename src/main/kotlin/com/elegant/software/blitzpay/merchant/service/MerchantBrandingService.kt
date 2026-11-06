package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.BrandingResponse
import com.elegant.software.blitzpay.merchant.api.BrandingSettings
import com.elegant.software.blitzpay.merchant.api.EffectiveBranding
import com.elegant.software.blitzpay.merchant.api.MerchantLogoUploadResponse
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranding
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.math.pow

@Service
@Transactional(readOnly = true)
class MerchantBrandingService(
    private val merchants: MerchantRepository,
    private val branches: MerchantBranchRepository,
    private val storage: StorageService,
) {
    fun get(merchantId: UUID, branchId: UUID? = null): BrandingResponse {
        val merchant = merchant(merchantId)
        val branch = branch(merchantId, branchId)
        return response(merchant, branch)
    }

    @Transactional
    fun replace(merchantId: UUID, branchId: UUID?, settings: BrandingSettings): BrandingResponse {
        val merchant = merchant(merchantId)
        val branch = branch(merchantId, branchId)
        require(settings.displayName == null || settings.displayName.isNotBlank()) { "displayName must not be blank" }
        require(settings.displayName == null || settings.displayName.length <= 120) { "displayName must be at most 120 characters" }
        listOfNotNull(settings.primaryColor, settings.accentColor).forEach(::textColor)
        settings.logoStorageKey?.let {
            val prefix = logoPrefix(merchantId, branchId)
            require(Regex("${Regex.escape(prefix)}[0-9a-f-]{36}\\.(jpg|png|webp)").matches(it)) {
                "logoStorageKey must belong to this branding scope"
            }
        }
        val value = MerchantBranding(settings.enabled, settings.displayName?.trim(), settings.logoStorageKey,
            settings.primaryColor?.uppercase(), settings.accentColor?.uppercase())
        if (branch == null) {
            merchant.branding = value
            merchant.lastUpdatedAt = Instant.now()
            merchants.save(merchant)
        } else {
            branch.branding = value
            branch.updatedAt = Instant.now()
            branches.save(branch)
        }
        return response(merchant, branch)
    }

    fun createUploadUrl(merchantId: UUID, branchId: UUID?, contentType: String): MerchantLogoUploadResponse {
        merchant(merchantId)
        branch(merchantId, branchId)
        val extension = when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException("contentType must be image/jpeg, image/png or image/webp")
        }
        // Unique keys keep unsaved uploads from overwriting published logos.
        val upload = storage.presignUpload("${logoPrefix(merchantId, branchId)}${UUID.randomUUID()}.$extension", contentType)
        return MerchantLogoUploadResponse(upload.storageKey, upload.uploadUrl, upload.expiresAt)
    }

    private fun merchant(id: UUID) = merchants.findById(id).orElseThrow { NoSuchElementException("Merchant not found: $id") }

    private fun branch(merchantId: UUID, id: UUID?): MerchantBranch? = id?.let {
        branches.findById(it).orElseThrow { NoSuchElementException("Branch not found: $id") }.also { branch ->
            if (branch.merchantId != merchantId) throw NoSuchElementException("Branch not found: $id")
        }
    }

    private fun response(merchant: Merchant, branch: MerchantBranch?): BrandingResponse {
        val parent = merchant.branding
        val child = branch?.branding
        val enabled = parent?.enabled == true && child?.enabled != false
        val primary = if (enabled) child?.primaryColor ?: parent?.primaryColor ?: DEFAULT_PRIMARY else DEFAULT_PRIMARY
        val accent = if (enabled) child?.accentColor ?: parent?.accentColor ?: DEFAULT_ACCENT else DEFAULT_ACCENT
        val logo = if (enabled) child?.logoStorageKey ?: parent?.logoStorageKey ?: merchant.logoStorageKey else merchant.logoStorageKey
        return BrandingResponse(
            settings = (if (branch == null) parent else child).toSettings(),
            logoPreviewUrl = (if (branch == null) parent else child)?.logoStorageKey?.let { storage.presignDownload(it) },
            effective = EffectiveBranding(
                enabled = enabled,
                displayName = if (enabled) child?.displayName ?: parent?.displayName ?: merchant.merchantName else merchant.merchantName,
                logoUrl = logo?.let { storage.presignDownload(it) },
                primaryColor = primary, primaryTextColor = textColor(primary),
                accentColor = accent, accentTextColor = textColor(accent),
            ),
        )
    }

    private fun MerchantBranding?.toSettings() = BrandingSettings(this?.enabled, this?.displayName,
        this?.logoStorageKey, this?.primaryColor, this?.accentColor)

    private fun logoPrefix(merchantId: UUID, branchId: UUID?) =
        "merchants/$merchantId/" + (branchId?.let { "branches/$it/" } ?: "") + "branding/"

    companion object {
        // Platform palette is deliberately separate from a merchant's colors.
        const val DEFAULT_PRIMARY = "#00C2FF"
        const val DEFAULT_ACCENT = "#5856D6"

        /** Choose black or white text with WCAG AA contrast (at least 4.5:1). */
        fun textColor(color: String): String {
            require(Regex("^#[0-9a-fA-F]{6}$").matches(color)) { "Color must be a six-digit hex value such as #2563EB" }
            val channels = listOf(1, 3, 5).map { offset ->
                val value = color.substring(offset, offset + 2).toInt(16) / 255.0
                if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            val luminance = channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
            return if ((luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)) "#000000" else "#FFFFFF"
        }
    }
}
