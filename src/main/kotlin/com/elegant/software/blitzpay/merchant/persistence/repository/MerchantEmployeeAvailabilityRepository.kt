package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeAvailability
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantEmployeeAvailabilityRepository : JpaRepository<MerchantEmployeeAvailability, UUID> {
    fun findAllByEmployeeIdAndBranchId(employeeId: UUID, branchId: UUID): List<MerchantEmployeeAvailability>
    fun findAllByEmployeeIdIn(employeeIds: Collection<UUID>): List<MerchantEmployeeAvailability>
}
