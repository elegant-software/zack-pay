package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMenuLanguage
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantMenuLanguageRepository : JpaRepository<MerchantMenuLanguage, String> {
    fun findAllByActiveTrueOrderByCodeAsc(): List<MerchantMenuLanguage>
}
