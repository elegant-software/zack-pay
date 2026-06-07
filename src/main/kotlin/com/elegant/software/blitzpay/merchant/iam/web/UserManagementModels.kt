package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import java.util.UUID

data class UserSearchResult(
    val id: String,
    val displayName: String,
    val email: String?,
    val enabled: Boolean,
    val groups: List<UserGroupMembership> = emptyList(),
)

data class UserGroupMembership(
    val id: String?,
    val merchantName: String,
    val branchName: String?,
)

data class MerchantUserAssignmentResponse(
    val id: UUID,
    val merchantId: UUID,
    val merchantName: String,
    val branchId: UUID?,
    val branchName: String?,
    val role: MerchantOperationsRole,
    val employeeId: UUID?,
)

data class CreateAssignmentRequest(
    val merchantId: UUID,
    val branchId: UUID?,
    val role: MerchantOperationsRole,
    val employeeId: UUID?,
)
