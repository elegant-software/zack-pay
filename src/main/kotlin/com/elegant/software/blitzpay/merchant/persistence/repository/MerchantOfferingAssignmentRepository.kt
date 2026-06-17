package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOfferingAssignment
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOfferingAssignmentId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantOfferingAssignmentRepository :
    JpaRepository<MerchantOfferingAssignment, MerchantOfferingAssignmentId> {
    fun findAllByMerchantApplicationId(merchantApplicationId: UUID): List<MerchantOfferingAssignment>
    fun existsByMerchantApplicationIdAndOfferingCode(merchantApplicationId: UUID, offeringCode: String): Boolean
}
