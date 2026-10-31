package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductTranslation
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductTranslationId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantProductTranslationRepository :
    JpaRepository<MerchantProductTranslation, MerchantProductTranslationId> {
    fun findAllByProductId(productId: UUID): List<MerchantProductTranslation>
    fun findAllByProductIdInAndLanguage(productIds: Collection<UUID>, language: String): List<MerchantProductTranslation>
    fun findByProductIdAndLanguage(productId: UUID, language: String): MerchantProductTranslation?
}
