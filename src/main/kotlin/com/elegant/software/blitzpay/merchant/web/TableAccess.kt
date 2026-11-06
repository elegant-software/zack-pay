package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.web.PlatformAdminAccess
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

class TableAccessDeniedException(message: String) : RuntimeException(message)

/**
 * Who may do what with a Branch's Tables (CONTEXT.md):
 * - setup (Tables, Floor Plans, Table Placements): a Merchant Manager of the Branch's Merchant, or a Platform Admin;
 * - live floor: any merchant user working the Branch, or a Platform Admin (read-only);
 * - seating guests and clearing a Table: merchant users of the Branch only; a Platform Admin only when they also work there.
 *
 * A Branch that does not belong to the Merchant in the path is reported as not found.
 */
@Component
class TableAccess(
    private val branchRepository: MerchantBranchRepository,
    private val callerResolver: MerchantCallerResolver,
) {

    fun requireSetup(merchantId: UUID, branchId: UUID, jwt: Jwt?): MerchantBranch {
        val branch = requireBranch(merchantId, branchId)
        if (PlatformAdminAccess.isPlatformAdmin(jwt) || isManager(jwt, merchantId)) return branch
        throw TableAccessDeniedException("Only a Merchant Manager or a Platform Admin may set up Tables and Floor Plans")
    }

    fun requireFloorRead(merchantId: UUID, branchId: UUID, jwt: Jwt?): MerchantBranch {
        val branch = requireBranch(merchantId, branchId)
        if (PlatformAdminAccess.isPlatformAdmin(jwt) || isBranchStaff(jwt, merchantId, branchId)) return branch
        throw TableAccessDeniedException("Caller does not work at this Branch")
    }

    fun requireFloorAction(merchantId: UUID, branchId: UUID, jwt: Jwt?): MerchantBranch {
        val branch = requireBranch(merchantId, branchId)
        // Working at the Branch decides, so an account that is also a Platform Admin still works its Tables.
        if (isBranchStaff(jwt, merchantId, branchId)) return branch
        if (PlatformAdminAccess.isPlatformAdmin(jwt)) {
            throw TableAccessDeniedException("A Platform Admin who does not work at this Branch may view its Tables but not seat guests or clear them")
        }
        throw TableAccessDeniedException("Caller does not work at this Branch")
    }

    /** For callers identified by Branch rather than Merchant (MCP tools): Merchant Manager or Platform Admin. */
    fun requireSetup(branchId: UUID, jwt: Jwt?): MerchantBranch {
        val branch = branchRepository.findById(branchId).orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        return requireSetup(branch.merchantId, branch.id, jwt)
    }

    private fun requireBranch(merchantId: UUID, branchId: UUID): MerchantBranch =
        branchRepository.findById(branchId).orElse(null)?.takeIf { it.merchantId == merchantId }
            ?: throw NoSuchElementException("Branch not found: $branchId")

    private fun isManager(jwt: Jwt?, merchantId: UUID): Boolean =
        assignments(jwt).any { it.merchantId == merchantId && it.role == MerchantOperationsRole.MERCHANT_MANAGER }

    /** A Merchant Manager, a merchant user assigned to the Branch (or Merchant-wide), or a member of the Branch group. */
    private fun isBranchStaff(jwt: Jwt?, merchantId: UUID, branchId: UUID): Boolean {
        if (isBranchGroupMember(jwt, branchId)) return true
        return assignments(jwt).any {
            it.merchantId == merchantId &&
                (it.role == MerchantOperationsRole.MERCHANT_MANAGER || it.branchId == null || it.branchId == branchId)
        }
    }

    private fun isBranchGroupMember(jwt: Jwt?, branchId: UUID): Boolean {
        val expected = KeycloakGroupNames.branchGroup(branchId)
        return jwt?.getClaimAsStringList("groups").orEmpty().any { it.substringAfterLast('/').equals(expected, ignoreCase = true) }
    }

    /** Same subject resolution as the other merchant endpoints ([MerchantCallerResolver]); none for a missing token. */
    private fun assignments(jwt: Jwt?) =
        jwt?.let { runCatching { callerResolver.resolve("Bearer ${it.tokenValue}", UUID(0, 0)).assignments }.getOrNull() }
            .orEmpty()
}
