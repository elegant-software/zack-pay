package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeBranchAssignment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantEmployeeBranchAssignmentRepository : JpaRepository<MerchantEmployeeBranchAssignment, UUID> {
    fun findAllByEmployeeId(employeeId: UUID): List<MerchantEmployeeBranchAssignment>
    fun findAllByBranchId(branchId: UUID): List<MerchantEmployeeBranchAssignment>
    fun existsByEmployeeIdAndBranchId(employeeId: UUID, branchId: UUID): Boolean
    fun findByEmployeeIdAndBranchId(employeeId: UUID, branchId: UUID): MerchantEmployeeBranchAssignment?
}
