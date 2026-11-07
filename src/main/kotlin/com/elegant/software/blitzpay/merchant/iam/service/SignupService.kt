package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.web.CreateAssignmentRequest
import com.elegant.software.blitzpay.merchant.service.MerchantBranchService
import com.elegant.software.blitzpay.merchant.service.MerchantRegistrationService
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class EmailConflictException : RuntimeException("email already registered")

/**
 * Self-service signup from the shopper and merchant apps: creates the Keycloak user the apps then
 * log in with (password grant). A merchant signup also creates the Merchant, a first (inactive) Branch
 * and a MERCHANT_MANAGER assignment, because the merchant app requires a Branch membership to sign in.
 */
@Service
@Profile("!contract-test")
class SignupService(
    private val keycloak: Keycloak,
    private val keycloakProperties: KeycloakProperties,
    private val merchantRegistrationService: MerchantRegistrationService,
    private val merchantBranchService: MerchantBranchService,
    private val groupSyncService: MerchantGroupSyncService,
    private val userManagementService: UserManagementService,
) {
    private val log = LoggerFactory.getLogger(SignupService::class.java)

    fun signupShopper(name: String, email: String, password: String): UUID =
        createUser(name, email, password)

    @Transactional
    fun signupMerchant(ownerName: String, merchantName: String, email: String, password: String, businessType: String): UUID {
        val userId = createUser(ownerName, email, password)
        try {
            // ponytail: operating country fixed to DE until signup asks for it
            val merchant = merchantRegistrationService.register(
                RegisterMerchantRequest(merchantName = merchantName, businessType = businessType, operatingCountry = "DE")
            )
            // Inactive: an active Branch needs an address and location, which the owner adds after signup.
            val branch = merchantBranchService.create(merchant.id, CreateBranchRequest(name = merchantName), active = false)
            groupSyncService.ensureBranchGroupExists(merchant.id, merchant.merchantName, branch.id, branch.name)
            userManagementService.createAssignment(
                userId,
                CreateAssignmentRequest(merchant.id, branch.id, MerchantOperationsRole.MERCHANT_MANAGER, employeeId = null),
            )
            log.info("merchant signup completed merchantId={} userId={}", merchant.id, userId)
            return merchant.id
        } catch (e: Exception) {
            // DB work rolls back with the transaction; the Keycloak user does not, so remove it.
            runCatching { users().delete(userId.toString()) }
                .onFailure { log.error("failed to remove keycloak user {} after merchant signup failure", userId, it) }
            throw e
        }
    }

    private fun createUser(name: String, email: String, password: String): UUID {
        val parts = name.trim().split(Regex("\\s+"), limit = 2)
        val rep = UserRepresentation().apply {
            username = email
            this.email = email
            firstName = parts[0]
            lastName = parts.getOrNull(1)
            isEnabled = true
            isEmailVerified = false
            credentials = listOf(CredentialRepresentation().apply {
                type = CredentialRepresentation.PASSWORD
                value = password
                isTemporary = false
            })
        }
        val (status, location) = users().create(rep).use { it.status to it.getHeaderString("Location") }
        if (status == Response.Status.CONFLICT.statusCode) throw EmailConflictException()
        // 400 here is usually the realm password policy rejecting the password.
        require(status in 200..299) { "Keycloak user creation failed with HTTP $status" }
        val id = location?.substringAfterLast("/") ?: error("No Location header in Keycloak user creation response")
        log.info("keycloak user created by signup userId={}", id)
        return UUID.fromString(id)
    }

    private fun users() = keycloak.realm(keycloakProperties.realm).users()
}
