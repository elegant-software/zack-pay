package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeServiceAssignment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantEmployeeServiceAssignmentRepository : JpaRepository<MerchantEmployeeServiceAssignment, UUID> {
    fun findAllByEmployeeId(employeeId: UUID): List<MerchantEmployeeServiceAssignment>
    fun findAllByEmployeeIdIn(employeeIds: Collection<UUID>): List<MerchantEmployeeServiceAssignment>
    fun existsByEmployeeIdAndProductCategoryId(employeeId: UUID, productCategoryId: UUID): Boolean
    fun findByEmployeeIdAndProductCategoryId(employeeId: UUID, productCategoryId: UUID): MerchantEmployeeServiceAssignment?
}
