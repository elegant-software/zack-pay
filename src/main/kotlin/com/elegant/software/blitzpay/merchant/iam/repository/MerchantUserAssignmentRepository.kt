package com.elegant.software.blitzpay.merchant.iam.repository

import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantUserAssignmentRepository : JpaRepository<MerchantUserAssignment, UUID> {
    fun findAllByKeycloakUserId(keycloakUserId: UUID): List<MerchantUserAssignment>
    fun findAllByMerchantApplicationId(merchantApplicationId: UUID): List<MerchantUserAssignment>
    fun existsByKeycloakUserIdAndMerchantApplicationIdAndBranchId(
        keycloakUserId: UUID,
        merchantApplicationId: UUID,
        branchId: UUID?,
    ): Boolean
}
