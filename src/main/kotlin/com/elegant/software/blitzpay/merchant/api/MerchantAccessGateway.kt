package com.elegant.software.blitzpay.merchant.api

import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

/** Who may act for a Merchant, for modules outside `merchant` (same rules as the Table endpoints). */
interface MerchantAccessGateway {
    /** A Platform Admin or a Merchant Manager of [merchantId]. */
    fun isManagerOrPlatformAdmin(merchantId: UUID, jwt: Jwt?): Boolean

    /** A merchant user working [branchId], which must belong to [merchantId]. */
    fun worksAtBranch(merchantId: UUID, branchId: UUID, jwt: Jwt?): Boolean
}
