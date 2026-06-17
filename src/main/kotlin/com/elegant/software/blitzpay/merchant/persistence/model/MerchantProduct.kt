package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.FilterDef
import org.hibernate.annotations.ParamDef
import org.hibernate.type.descriptor.java.UUIDJavaType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@FilterDef(
    name = "tenantFilter",
    parameters = [ParamDef(name = "merchantId", type = UUIDJavaType::class)]
)
@Filter(name = "tenantFilter", condition = "merchant_application_id = :merchantId")
@Entity
@Table(name = "merchant_products", schema = "blitzpay")
class MerchantProduct(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(nullable = false)
    var name: String,

    @Column(length = 2000)
    var description: String? = null,

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 4)
    var unitPrice: BigDecimal,

    @Column(name = "image_storage_key")
    var imageStorageKey: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false, length = 32)
    var status: String = MerchantEntityStatus.INACTIVE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,

    @Column(name = "merchant_branch_id")
    var merchantBranchId: UUID? = null,

    @Column(name = "product_category_id")
    var productCategoryId: UUID? = null,

    @Column(name = "product_code")
    var productCode: Long? = null,
) {
    fun deactivate(at: Instant = Instant.now()) {
        active = false
        updatedAt = at
    }

    fun update(
        name: String,
        description: String?,
        unitPrice: BigDecimal,
        imageStorageKey: String?,
        productCategoryId: UUID? = this.productCategoryId,
        productCode: Long? = this.productCode,
        at: Instant = Instant.now()
    ) {
        require(name.isNotBlank()) { "name must not be blank" }
        require(description == null || description.length <= 2_000) { "description must be <= 2000 characters" }
        require(unitPrice >= BigDecimal.ZERO) { "unitPrice must be >= 0" }
        require(productCode == null || productCode > 0) { "productCode must be > 0" }
        this.name = name.trim()
        this.description = description?.trim()?.ifBlank { null }
        this.unitPrice = unitPrice
        this.imageStorageKey = imageStorageKey
        this.productCategoryId = productCategoryId
        this.productCode = productCode
        this.active = true
        this.status = MerchantEntityStatus.ACTIVE
        this.updatedAt = at
    }
}
