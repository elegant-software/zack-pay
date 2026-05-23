package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantStripeConnect
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantStripeConnectRepository : JpaRepository<MerchantStripeConnect, UUID> {
    fun findByConnectedAccountId(connectedAccountId: String): MerchantStripeConnect?
}