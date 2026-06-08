package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.iam.repository.MerchantUserAssignmentRepository
import org.springframework.stereotype.Component
import java.util.Base64
import java.util.UUID

data class MerchantCaller(
    val keycloakUserId: UUID,
    val assignments: List<MerchantUserAssignment>,
) {
    fun isManagerFor(merchantId: UUID): Boolean =
        assignments.any { it.merchantApplicationId == merchantId && it.role == MerchantOperationsRole.MERCHANT_MANAGER }

    fun isEmployeeFor(merchantId: UUID): Boolean =
        assignments.any { it.merchantApplicationId == merchantId && it.role == MerchantOperationsRole.MERCHANT_EMPLOYEE }

    fun employeeIdFor(merchantId: UUID): UUID? =
        assignments.firstOrNull { it.merchantApplicationId == merchantId && it.role == MerchantOperationsRole.MERCHANT_EMPLOYEE }
            ?.employeeId

    fun requireManager(merchantId: UUID) {
        check(isManagerFor(merchantId)) { "Caller is not a Merchant Manager for this merchant" }
    }

    fun requireManagerOrEmployee(merchantId: UUID) {
        check(isManagerFor(merchantId) || isEmployeeFor(merchantId)) {
            "Caller has no appointment management role for this merchant"
        }
    }
}

@Component
class MerchantCallerResolver(
    private val assignmentRepo: MerchantUserAssignmentRepository,
) {
    fun resolve(authorization: String?, merchantId: UUID): MerchantCaller {
        val userId = extractSubject(authorization)
            ?: throw IllegalStateException("Missing or invalid Authorization header")
        val assignments = assignmentRepo.findAllByKeycloakUserId(userId)
        return MerchantCaller(userId, assignments)
    }

    private fun extractSubject(authorization: String?): UUID? {
        val token = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            val sub = Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.getOrNull(1)
            sub?.let { UUID.fromString(it) }
        }.getOrNull()
    }
}
