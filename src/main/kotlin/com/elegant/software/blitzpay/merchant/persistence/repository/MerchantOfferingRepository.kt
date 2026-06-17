package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOffering
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantOfferingRepository : JpaRepository<MerchantOffering, String> {
    fun findAllByActiveTrueOrderByDisplayNameAsc(): List<MerchantOffering>
}
