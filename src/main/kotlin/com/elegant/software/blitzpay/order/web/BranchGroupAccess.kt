package com.elegant.software.blitzpay.order.web

import org.springframework.http.HttpStatus
import com.elegant.software.blitzpay.config.ApiException
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

class BranchAccessDeniedException(message: String) : ApiException(HttpStatus.FORBIDDEN, message)

/**
 * Branch-scoped authorization from the Keycloak `groups` token claim. Branch groups are named `branch_<branchId>`
 * (see `KeycloakGroupNames` in merchant.iam); the claim may carry plain names or full paths
 * (`/merchants/merchant_<id>/branch_<id>`), so only the last path segment is compared.
 */
object BranchGroupAccess {
    private const val GROUPS_CLAIM = "groups"
    private const val BRANCH_GROUP_PREFIX = "branch_"

    fun isMember(jwt: Jwt?, branchId: UUID): Boolean {
        val groups = jwt?.getClaimAsStringList(GROUPS_CLAIM).orEmpty()
        val expected = "$BRANCH_GROUP_PREFIX$branchId"
        return groups.any { it.substringAfterLast('/').equals(expected, ignoreCase = true) }
    }

    /** The Branches whose groups the caller is in. */
    fun branchIds(jwt: Jwt?): List<UUID> = jwt?.getClaimAsStringList(GROUPS_CLAIM).orEmpty().mapNotNull {
        it.substringAfterLast('/').takeIf { name -> name.startsWith(BRANCH_GROUP_PREFIX, ignoreCase = true) }
            ?.let { name -> runCatching { UUID.fromString(name.substring(BRANCH_GROUP_PREFIX.length)) }.getOrNull() }
    }.distinct()

    fun require(jwt: Jwt?, branchId: UUID) {
        if (!isMember(jwt, branchId)) throw BranchAccessDeniedException("Caller is not a member of branch $branchId")
    }
}
