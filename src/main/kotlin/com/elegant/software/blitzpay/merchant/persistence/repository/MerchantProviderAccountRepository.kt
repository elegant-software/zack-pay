package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProviderAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantProviderAccountRepository : JpaRepository<MerchantProviderAccount, UUID> {
    fun findByMerchantIdAndPspCode(merchantId: UUID, pspCode: String): MerchantProviderAccount?
    fun findAllByMerchantId(merchantId: UUID): List<MerchantProviderAccount>
    fun existsByMerchantIdAndPspCodeAndEnabledTrue(merchantId: UUID, pspCode: String): Boolean
}
