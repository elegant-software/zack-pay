package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class MerchantProductCategoryTranslationId(
    val categoryId: UUID = UUID.randomUUID(),
    val language: String = "",
) : Serializable

@Entity
@IdClass(MerchantProductCategoryTranslationId::class)
@Table(name = "merchant_product_category_translations", schema = "blitzpay")
class MerchantProductCategoryTranslation(
    @Id
    @Column(name = "category_id", nullable = false, updatable = false)
    val categoryId: UUID,

    @Id
    @Column(nullable = false, length = 8, updatable = false)
    val language: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var origin: TranslationOrigin,

    @Column(name = "source_hash", nullable = false, length = 64)
    var sourceHash: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    fun revise(name: String, origin: TranslationOrigin, sourceHash: String, at: Instant = Instant.now()) {
        this.name = name
        this.origin = origin
        this.sourceHash = sourceHash
        this.updatedAt = at
    }
}
