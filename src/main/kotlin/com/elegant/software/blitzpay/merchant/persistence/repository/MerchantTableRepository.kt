package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantTableRepository : JpaRepository<MerchantTable, UUID> {
    fun findAllByBranchIdOrderByCreatedAtAsc(branchId: UUID): List<MerchantTable>
    fun findAllByBranchIdAndActiveTrue(branchId: UUID): List<MerchantTable>
    fun findAllByFloorPlanId(floorPlanId: UUID): List<MerchantTable>
    fun findByCode(code: String): MerchantTable?
    fun existsByCode(code: String): Boolean
    fun deleteAllByBranchIdIn(branchIds: Collection<UUID>)
}
