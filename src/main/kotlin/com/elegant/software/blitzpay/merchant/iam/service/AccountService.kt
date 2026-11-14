package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.api.AccountDeleted
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakAdminClientConfig
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.repository.AccountConsentRepository
import com.elegant.software.blitzpay.merchant.iam.repository.MerchantUserAssignmentRepository
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AccountExport(
    val userId: UUID,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
    val emailVerified: Boolean,
    val createdAt: Instant?,
    val assignments: List<Assignment>,
    val consents: List<Consent>,
) {
    data class Assignment(val merchantId: UUID, val branchId: UUID?, val role: String, val createdAt: Instant)
    data class Consent(val termsVersion: String, val acceptedAt: Instant)
}

/**
 * The signed-in user's own account: data export (GDPR Art. 15/20) and erasure (Art. 17).
 * [issuer] is the token's `iss`: it decides whether the account lives in the customer or the business realm.
 */
@Service
@Profile("!contract-test")
class AccountService(
    private val keycloak: Keycloak,
    @Qualifier(KeycloakAdminClientConfig.CUSTOMER_KEYCLOAK) private val customerKeycloak: Keycloak,
    private val keycloakProperties: KeycloakProperties,
    private val assignments: MerchantUserAssignmentRepository,
    private val consents: AccountConsentRepository,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(AccountService::class.java)

    fun export(userId: UUID, issuer: String?): AccountExport {
        val user = users(issuer).get(userId.toString()).toRepresentation()
        return AccountExport(
            userId = userId,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            emailVerified = user.isEmailVerified,
            createdAt = user.createdTimestamp?.let(Instant::ofEpochMilli),
            assignments = assignments.findAllByKeycloakUserId(userId)
                .map { AccountExport.Assignment(it.merchantId, it.branchId, it.role.name, it.createdAt) },
            consents = consents.findAllByKeycloakUserId(userId)
                .map { AccountExport.Consent(it.termsVersion, it.acceptedAt) },
        )
    }

    /**
     * Deletes the login, its Merchant/Branch assignments and consent records, and tells other modules to erase
     * their data for this user. Business records (merchant, orders, invoices) stay: tax law keeps them 10 years.
     */
    // ponytail: the last manager of a merchant may delete themselves; platform admins can still manage that merchant.
    @Transactional
    fun delete(userId: UUID, issuer: String?) {
        assignments.deleteAll(assignments.findAllByKeycloakUserId(userId))
        consents.deleteAllByKeycloakUserId(userId)
        events.publishEvent(AccountDeleted(userId.toString()))
        // Last, so a failure above leaves a working login the user can retry with.
        val status = users(issuer).delete(userId.toString()).use { it.status }
        check(status in 200..299 || status == 404) { "Keycloak user deletion failed with HTTP $status" }
        log.info("account deleted by its owner userId={}", userId)
    }

    private fun users(issuer: String?) =
        if (keycloakProperties.isCustomerIssuer(issuer)) customerKeycloak.realm(keycloakProperties.customerRealm).users()
        else keycloak.realm(keycloakProperties.realm).users()
}
