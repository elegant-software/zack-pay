package com.elegant.software.blitzpay.order.api

/** Published when staff confirm a payment taken outside BlitzPay (e.g. Cash Payment) and the Order has a linked shopper. */
data class OrderManuallySettled(
    val orderId: String,
    val shopperId: String,
)
