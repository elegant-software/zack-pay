package com.elegant.software.blitzpay.order.persistence.model

/** Kitchen-side lifecycle of an Order; independent of the payment [OrderStatus]. See CONTEXT.md "Preparation Status". */
enum class PreparationStatus {
    RECEIVED,
    PREPARING,
    READY;

    /** The Kitchen Screen only moves an Order one step forward; going back to RECEIVED happens via an item addition. */
    fun canAdvanceTo(next: PreparationStatus): Boolean =
        (this == RECEIVED && next == PREPARING) || (this == PREPARING && next == READY)
}
