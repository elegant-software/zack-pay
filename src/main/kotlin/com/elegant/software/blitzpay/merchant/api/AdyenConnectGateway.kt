package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

interface AdyenConnectGateway {
    fun save(merchantId: UUID, legalEntityId: String, accountHolderId: String, balanceAccountId: String)
    fun onAccountUpdated(balanceAccountId: String, receivePaymentsEnabled: Boolean, detailsSubmitted: Boolean)
    fun findLegalEntityId(merchantId: UUID): String?
    fun findBalanceAccountId(merchantId: UUID): String?
    fun isReceivePaymentsEnabled(merchantId: UUID): Boolean
    fun getDetails(merchantId: UUID): MerchantDetailsResponse
}
