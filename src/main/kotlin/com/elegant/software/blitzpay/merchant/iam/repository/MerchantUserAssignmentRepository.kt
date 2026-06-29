package com.elegant.software.blitzpay.merchant.iam.repository

import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantUserAssignmentRepository : JpaRepository<MerchantUserAssignment, UUID> {
    fun findAllByKeycloakUserId(keycloakUserId: UUID): List<MerchantUserAssignment>
    fun findAllByMerchantId(merchantId: UUID): List<MerchantUserAssignment>
    fun existsByKeycloakUserIdAndMerchantIdAndBranchId(
        keycloakUserId: UUID,
        merchantId: UUID,
        branchId: UUID?,
    ): Boolean
}
