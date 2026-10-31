package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantSupportedMenuLanguage
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantSupportedMenuLanguageId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantSupportedMenuLanguageRepository :
    JpaRepository<MerchantSupportedMenuLanguage, MerchantSupportedMenuLanguageId> {
    fun findAllByMerchantId(merchantId: UUID): List<MerchantSupportedMenuLanguage>
    fun deleteAllByMerchantId(merchantId: UUID)
}
