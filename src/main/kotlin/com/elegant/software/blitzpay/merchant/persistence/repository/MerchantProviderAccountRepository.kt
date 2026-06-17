package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProviderAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantProviderAccountRepository : JpaRepository<MerchantProviderAccount, UUID> {
    fun findByMerchantApplicationIdAndPspCode(merchantApplicationId: UUID, pspCode: String): MerchantProviderAccount?
    fun findAllByMerchantApplicationId(merchantApplicationId: UUID): List<MerchantProviderAccount>
    fun existsByMerchantApplicationIdAndPspCodeAndEnabledTrue(merchantApplicationId: UUID, pspCode: String): Boolean
}
