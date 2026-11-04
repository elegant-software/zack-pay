package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantFloorPlan
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantFloorPlanRepository : JpaRepository<MerchantFloorPlan, UUID> {
    fun findAllByBranchIdOrderByCreatedAtAsc(branchId: UUID): List<MerchantFloorPlan>
    fun deleteAllByBranchIdIn(branchIds: Collection<UUID>)
}
