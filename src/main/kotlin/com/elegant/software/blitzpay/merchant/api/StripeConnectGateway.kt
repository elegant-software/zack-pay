package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

interface StripeConnectGateway {
    fun save(merchantId: UUID, connectedAccountId: String)
    fun onAccountUpdated(
        connectedAccountId: String,
        chargesEnabled: Boolean,
        detailsSubmitted: Boolean,
        payByBankEnabled: Boolean,
    )
    fun findConnectedAccountId(merchantId: UUID): String?
    fun isChargesEnabled(merchantId: UUID): Boolean
    fun isDetailsSubmitted(merchantId: UUID): Boolean
    fun isPayByBankEnabled(merchantId: UUID): Boolean
    fun getDetails(merchantId: UUID): MerchantDetailsResponse
}
