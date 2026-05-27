package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantAdyenProviderAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantAdyenProviderAccountRepository : JpaRepository<MerchantAdyenProviderAccount, UUID> {
    fun findByLegalEntityId(legalEntityId: String): MerchantAdyenProviderAccount?
    fun findByBalanceAccountId(balanceAccountId: String): MerchantAdyenProviderAccount?
}
