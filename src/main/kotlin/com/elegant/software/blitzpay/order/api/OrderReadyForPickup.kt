package com.elegant.software.blitzpay.order.api

import java.util.UUID

/** Published when an Order's Preparation Status becomes READY and the Order has a linked shopper. */
data class OrderReadyForPickup(
    val orderId: String,
    val orderIdFk: UUID,
    val branchId: UUID?,
    val pickupNumber: Int?,
    val shopperId: String,
    val fulfilmentType: FulfilmentType? = null,
    /** True when the order was placed at a Table; with DINE_IN it is brought there instead of collected. */
    val atTable: Boolean = false,
)
