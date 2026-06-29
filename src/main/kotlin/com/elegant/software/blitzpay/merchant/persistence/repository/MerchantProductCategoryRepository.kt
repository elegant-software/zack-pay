package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantProductCategoryRepository : JpaRepository<MerchantProductCategory, UUID> {
    fun findAllByMerchantId(merchantId: UUID): List<MerchantProductCategory>
    fun findByMerchantIdAndId(merchantId: UUID, id: UUID): MerchantProductCategory?
    fun findByMerchantIdAndNameIgnoreCase(
        merchantId: UUID,
        name: String
    ): MerchantProductCategory?

    fun existsByIdAndMerchantId(id: UUID, merchantId: UUID): Boolean
    fun existsByMerchantIdAndNameIgnoreCase(merchantId: UUID, name: String): Boolean
}
