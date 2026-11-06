package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantThemeRequest
import com.elegant.software.blitzpay.merchant.api.MerchantThemeResponse
import com.elegant.software.blitzpay.merchant.api.ThemeApp
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTheme
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantThemeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MerchantThemeService(
    private val merchants: MerchantRepository,
    private val themes: MerchantThemeRepository,
) {
    fun list(merchantId: UUID, app: ThemeApp = ThemeApp.SHOPPER): List<MerchantThemeResponse> {
        requireMerchant(merchantId)
        return themes.findAllByMerchantIdAndAppOrderByNameAscIdAsc(merchantId, app).map(::response)
    }

    @Transactional
    fun create(merchantId: UUID, request: MerchantThemeRequest, app: ThemeApp = ThemeApp.SHOPPER): MerchantThemeResponse {
        requireMerchant(merchantId)
        validate(request)
        return response(themes.save(MerchantTheme(merchantId = merchantId, name = request.name.trim(),
            primaryColor = request.primaryColor.uppercase(), accentColor = request.accentColor.uppercase(), app = app)))
    }

    @Transactional
    fun update(merchantId: UUID, themeId: UUID, request: MerchantThemeRequest, app: ThemeApp = ThemeApp.SHOPPER): MerchantThemeResponse {
        requireMerchant(merchantId)
        val theme = themes.findByIdAndMerchantIdAndApp(themeId, merchantId, app)
            ?: throw NoSuchElementException("Theme not found: $themeId")
        validate(request)
        theme.name = request.name.trim()
        theme.primaryColor = request.primaryColor.uppercase()
        theme.accentColor = request.accentColor.uppercase()
        return response(themes.save(theme))
    }

    private fun requireMerchant(id: UUID) {
        if (!merchants.existsById(id)) throw NoSuchElementException("Merchant not found: $id")
    }

    private fun validate(request: MerchantThemeRequest) {
        require(request.name.isNotBlank() && request.name.length <= 120) { "Theme name must contain 1 to 120 characters" }
        MerchantBrandingService.textColor(request.primaryColor)
        MerchantBrandingService.textColor(request.accentColor)
    }

    private fun response(theme: MerchantTheme) = MerchantThemeResponse(
        theme.id, theme.merchantId, theme.name, theme.primaryColor,
        MerchantBrandingService.textColor(theme.primaryColor), theme.accentColor,
        MerchantBrandingService.textColor(theme.accentColor),
        theme.app,
    )
}
