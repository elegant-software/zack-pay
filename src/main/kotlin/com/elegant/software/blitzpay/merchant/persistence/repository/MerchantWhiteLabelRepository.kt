package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.api.ThemeApp
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantWhiteLabel
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantWhiteLabelRepository : JpaRepository<MerchantWhiteLabel, UUID> {
    fun findByMerchantIdAndBranchIdAndApp(merchantId: UUID, branchId: UUID?, app: ThemeApp): MerchantWhiteLabel?
}
