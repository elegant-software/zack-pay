package com.elegant.software.blitzpay.merchant.iam.outbound

import java.util.UUID

object KeycloakGroupNames {
    const val MOBILE_USERS = "blitzpay-mobile-users"
    const val ROOT = "merchants"
    const val MERCHANT_APP_USERS = "merchant-app-users"
    const val MERCHANT_MANAGER = "merchant_manager"
    const val MERCHANT_EMPLOYEE = "merchant_employee"
    const val MERCHANT_PREFIX = "merchant_"
    const val BRANCH_PREFIX = "branch_"
    const val ATTR_MERCHANT_ID = "merchant_id"
    const val ATTR_MERCHANT_NAME = "merchant_name"
    const val ATTR_BRANCH_ID = "branch_id"
    const val ATTR_BRANCH_NAME = "branch_name"

    fun merchantGroup(merchantId: UUID) = "$MERCHANT_PREFIX$merchantId"
    fun branchGroup(branchId: UUID) = "$BRANCH_PREFIX$branchId"
}
