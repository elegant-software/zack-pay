package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOfferingAssignment
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOfferingAssignmentId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantOfferingAssignmentRepository :
    JpaRepository<MerchantOfferingAssignment, MerchantOfferingAssignmentId> {
    fun findAllByMerchantId(merchantId: UUID): List<MerchantOfferingAssignment>
    fun existsByMerchantIdAndOfferingCode(merchantId: UUID, offeringCode: String): Boolean
}
