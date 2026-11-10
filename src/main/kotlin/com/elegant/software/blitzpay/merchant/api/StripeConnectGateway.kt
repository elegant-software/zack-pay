package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

data class BranchTerminalInfo(
    val branchName: String,
    val addressLine1: String?,
    val city: String?,
    val postalCode: String?,
    val country: String?,
)

/** A Branch that has a Stripe Terminal Location. */
data class BranchTerminalLocation(
    val branchId: UUID,
    val branchName: String,
    val locationId: String,
)

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
    fun isTapToPayEnabled(merchantId: UUID): Boolean
    fun enableTapToPay(merchantId: UUID)
    fun disableTapToPay(merchantId: UUID)
    fun findTerminalLocationId(branchId: UUID): String?
    fun saveTerminalLocationId(branchId: UUID, locationId: String)
    /** The merchant's Branches that have a Stripe Terminal Location; Branches without one are left out. */
    fun findTerminalLocations(merchantId: UUID): List<BranchTerminalLocation>
    fun getBranchTerminalInfo(branchId: UUID): BranchTerminalInfo?
    fun getDetails(merchantId: UUID): MerchantDetailsResponse
}
