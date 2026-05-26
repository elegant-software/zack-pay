package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantStripeProviderAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantStripeProviderAccountRepository : JpaRepository<MerchantStripeProviderAccount, UUID> {
    fun findByConnectedAccountId(connectedAccountId: String): MerchantStripeProviderAccount?
}
