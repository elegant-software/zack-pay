package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

interface MerchantLookupGateway {
    fun requireMerchant(merchantId: UUID): MerchantReference
    fun findMerchant(merchantId: UUID): MerchantReference?
    fun findBranch(branchId: UUID): BranchReference?
}

data class MerchantReference(val id: UUID, val name: String)
data class BranchReference(val id: UUID, val merchantApplicationId: UUID, val name: String?)
