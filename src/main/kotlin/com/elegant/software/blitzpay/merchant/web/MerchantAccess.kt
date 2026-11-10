package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MerchantAccessGateway
import com.elegant.software.blitzpay.merchant.iam.web.PlatformAdminAccess
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

/** [MerchantAccessGateway] on the same caller rules as [TableAccess]. */
@Component
class MerchantAccess(
    private val tableAccess: TableAccess,
    private val branchRepository: MerchantBranchRepository,
) : MerchantAccessGateway {

    override fun isManagerOrPlatformAdmin(merchantId: UUID, jwt: Jwt?): Boolean =
        PlatformAdminAccess.isPlatformAdmin(jwt) || tableAccess.isManager(jwt, merchantId)

    override fun worksAtBranch(merchantId: UUID, branchId: UUID, jwt: Jwt?): Boolean =
        branchRepository.findById(branchId).orElse(null)?.merchantId == merchantId &&
            tableAccess.isBranchStaff(jwt, merchantId, branchId)
}
