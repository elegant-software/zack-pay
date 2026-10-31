package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategoryTranslation
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategoryTranslationId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantProductCategoryTranslationRepository :
    JpaRepository<MerchantProductCategoryTranslation, MerchantProductCategoryTranslationId> {
    fun findAllByCategoryId(categoryId: UUID): List<MerchantProductCategoryTranslation>
    fun findAllByCategoryIdInAndLanguage(categoryIds: Collection<UUID>, language: String): List<MerchantProductCategoryTranslation>
    fun findByCategoryIdAndLanguage(categoryId: UUID, language: String): MerchantProductCategoryTranslation?
    fun deleteAllByCategoryId(categoryId: UUID)
}
