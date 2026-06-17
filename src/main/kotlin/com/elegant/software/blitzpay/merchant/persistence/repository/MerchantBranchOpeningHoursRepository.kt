package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranchOpeningHours
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantBranchOpeningHoursRepository : JpaRepository<MerchantBranchOpeningHours, UUID> {
    fun findAllByBranchId(branchId: UUID): List<MerchantBranchOpeningHours>
}
