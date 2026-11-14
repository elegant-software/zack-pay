package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.api.AccountDeleted
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.domain.AccountConsent
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.iam.repository.AccountConsentRepository
import com.elegant.software.blitzpay.merchant.iam.repository.MerchantUserAssignmentRepository
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID
import kotlin.test.assertEquals

class AccountServiceTest {

    private val keycloak = mock<Keycloak>()
    private val realm = mock<RealmResource>()
    private val users = mock<UsersResource>()
    private val assignments = mock<MerchantUserAssignmentRepository>()
    private val consents = mock<AccountConsentRepository>()
    private val events = mock<ApplicationEventPublisher>()
    private val service = AccountService(
        keycloak, keycloak, KeycloakProperties("https://auth.example", "blitzpay", "admin", "secret"), assignments, consents, events,
    )
    private val userId = UUID.randomUUID()
    private val assignment = MerchantUserAssignment(
        keycloakUserId = userId, merchantId = UUID.randomUUID(), role = MerchantOperationsRole.MERCHANT_MANAGER,
    )

    @BeforeEach
    fun setUp() {
        whenever(keycloak.realm("blitzpay")) doReturn realm
        whenever(realm.users()) doReturn users
        whenever(assignments.findAllByKeycloakUserId(userId)) doReturn listOf(assignment)
    }

    @Test
    fun `export returns the profile, assignments and consents of the account`() {
        val user = mock<UserResource>()
        whenever(users.get(userId.toString())) doReturn user
        whenever(user.toRepresentation()) doReturn UserRepresentation().apply {
            email = "ada@example.com"; firstName = "Ada"; isEmailVerified = true; createdTimestamp = 0
        }
        whenever(consents.findAllByKeycloakUserId(userId)) doReturn
            listOf(AccountConsent(keycloakUserId = userId, termsVersion = "2026-11-08"))

        val export = service.export(userId, BUSINESS_ISSUER)

        assertEquals("ada@example.com", export.email)
        assertEquals("MERCHANT_MANAGER", export.assignments.single().role)
        assertEquals("2026-11-08", export.consents.single().termsVersion)
    }

    @Test
    fun `delete erases assignments, consents and the login and tells other modules`() {
        whenever(users.delete(userId.toString())) doReturn Response.noContent().build()

        service.delete(userId, BUSINESS_ISSUER)

        verify(assignments).deleteAll(listOf(assignment))
        verify(consents).deleteAllByKeycloakUserId(userId)
        verify(events).publishEvent(AccountDeleted(userId.toString()))
        verify(users).delete(userId.toString())
    }

    @Test
    fun `failed Keycloak deletion fails the request so the transaction rolls back`() {
        whenever(users.delete(userId.toString())) doReturn Response.serverError().build()

        assertThrows<IllegalStateException> { service.delete(userId, BUSINESS_ISSUER) }
    }

    @Test
    fun `a customer realm token deletes the login in the customer realm`() {
        val customerKeycloak = mock<Keycloak>()
        val customerRealm = mock<RealmResource>()
        val customerUsers = mock<UsersResource>()
        whenever(customerKeycloak.realm("blitzpay-customers")) doReturn customerRealm
        whenever(customerRealm.users()) doReturn customerUsers
        whenever(customerUsers.delete(userId.toString())) doReturn Response.noContent().build()
        val split = AccountService(
            keycloak, customerKeycloak,
            KeycloakProperties("https://auth.example", "blitzpay", "admin", "secret", customerRealm = "blitzpay-customers"),
            assignments, consents, events,
        )

        split.delete(userId, "https://auth.example/realms/blitzpay-customers")

        verify(customerUsers).delete(userId.toString())
        verify(users, never()).delete(any())
    }

    private companion object {
        const val BUSINESS_ISSUER = "https://auth.example/realms/blitzpay"
    }
}
