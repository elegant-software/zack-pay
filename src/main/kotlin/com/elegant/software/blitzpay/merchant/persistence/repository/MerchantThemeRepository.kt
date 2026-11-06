package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTheme
import com.elegant.software.blitzpay.merchant.api.ThemeApp
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantThemeRepository : JpaRepository<MerchantTheme, UUID> {
    fun findAllByMerchantIdAndAppOrderByNameAscIdAsc(merchantId: UUID, app: ThemeApp): List<MerchantTheme>
    fun findByIdAndMerchantIdAndApp(id: UUID, merchantId: UUID, app: ThemeApp): MerchantTheme?
}
