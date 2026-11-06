package com.elegant.software.blitzpay.order.persistence.model

import com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "order_items", schema = "blitzpay")
class OrderItem(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "order_id_fk", nullable = false, updatable = false)
    val orderIdFk: UUID,

    @Column(name = "merchant_product_id", nullable = false, updatable = false)
    val merchantProductId: UUID,

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(name = "merchant_branch_id")
    val merchantBranchId: UUID? = null,

    @Column(name = "product_name", nullable = false, length = 255)
    val productName: String,

    @Column(name = "product_description", length = 2000)
    val productDescription: String? = null,

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "unit_price_minor", nullable = false)
    val unitPriceMinor: Long,

    @Column(name = "line_total_minor", nullable = false)
    var lineTotalMinor: Long,

    @Column(name = "merchant_price_override_minor")
    var merchantPriceOverrideMinor: Long? = null,

    @Column(name = "overridden_by", length = 255)
    var overriddenBy: String? = null,

    @Column(name = "overridden_at")
    var overriddenAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** Already made by the kitchen: the Order was READY when another item was added. Locked items can't change. */
    @Column(nullable = false)
    var locked: Boolean = false,

    /** Immutable routing snapshot: catalog changes never move submitted work. */
    @Column(name = "preparation_station_id", nullable = false, updatable = false)
    val preparationStationId: UUID = UUID(0, 0),
    @Column(name = "preparation_station_name", nullable = false, updatable = false, length = 100)
    val preparationStationName: String = "Unassigned",
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "preparation_status", nullable = false, length = 16)
    var preparationStatus: PreparationStatus = PreparationStatus.RECEIVED,
) {
    val effectiveUnitPriceMinor: Long
        get() = merchantPriceOverrideMinor ?: unitPriceMinor

    fun applyPriceOverride(priceMinor: Long, by: String, at: Instant = Instant.now()) {
        require(priceMinor > 0) { "Override price must be positive" }
        merchantPriceOverrideMinor = priceMinor
        lineTotalMinor = priceMinor * quantity
        overriddenBy = by
        overriddenAt = at
    }

    fun updateQuantity(newQuantity: Int) {
        require(newQuantity > 0) { "quantity must be > 0" }
        quantity = newQuantity
        lineTotalMinor = effectiveUnitPriceMinor * newQuantity
    }
    companion object {
        fun fromProduct(orderIdFk: UUID, product: OrderableMerchantProduct, quantity: Int): OrderItem {
            val unitPriceMinor = product.unitPriceMinor()
            return OrderItem(
                orderIdFk = orderIdFk,
                merchantProductId = product.productId,
                merchantApplicationId = product.merchantId,
                merchantBranchId = product.branchId,
                productName = product.name,
                preparationStationId = product.preparationStationId,
                preparationStationName = product.preparationStationName,
                productDescription = product.description,
                quantity = quantity,
                unitPriceMinor = unitPriceMinor,
                lineTotalMinor = unitPriceMinor * quantity,
            )
        }
    }
}

internal fun OrderableMerchantProduct.unitPriceMinor(): Long =
    unitPrice.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
