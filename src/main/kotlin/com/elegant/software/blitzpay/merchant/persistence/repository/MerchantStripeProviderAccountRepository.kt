package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantStripeProviderAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantStripeProviderAccountRepository : JpaRepository<MerchantStripeProviderAccount, UUID> {
    fun findByConnectedAccountId(connectedAccountId: String): MerchantStripeProviderAccount?
}
