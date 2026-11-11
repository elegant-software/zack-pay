package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.domain.AccountConsent
import com.elegant.software.blitzpay.merchant.iam.repository.AccountConsentRepository
import com.elegant.software.blitzpay.support.TestFixtureLoader
import com.elegant.software.blitzpay.merchant.service.MerchantBranchService
import com.elegant.software.blitzpay.merchant.service.MerchantRegistrationService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.admin.client.resource.GroupsResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SignupServiceTest {

    private val keycloak = mock<Keycloak>()
    private val realm = mock<RealmResource>()
    private val users = mock<UsersResource>()
    private val groups = mock<GroupsResource>()
    private val user = mock<UserResource>()
    private val mobileGroup = TestFixtureLoader.load("testdata/merchant/shopper-group.json", GroupRepresentation::class.java)
    private val registration = mock<MerchantRegistrationService>()
    private val branches = mock<MerchantBranchService>()
    private val consents = mock<AccountConsentRepository>()
    private val service = SignupService(
        keycloak,
        KeycloakProperties("https://auth.example", "blitzpay", "admin", "secret"),
        registration, branches, mock(), mock(), consents,
    )
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        whenever(keycloak.realm("blitzpay")) doReturn realm
        whenever(realm.users()) doReturn users
        whenever(realm.groups()) doReturn groups
        whenever(groups.groups("blitzpay-mobile-users", true, 0, 10, false)) doReturn listOf(mobileGroup)
        whenever(users.get(userId.toString())) doReturn user
    }

    @Test
    fun `shopper signup creates an enabled Keycloak user with email as username`() {
        whenever(users.create(any())) doReturn Response.created(URI("https://auth.example/users/$userId")).build()

        assertEquals(userId, service.signupShopper("Ada Lovelace", "ada@example.com", "Secret123"))

        val rep = argumentCaptor<UserRepresentation>().apply { verify(users).create(capture()) }.firstValue
        assertEquals("ada@example.com", rep.username)
        assertEquals("Ada", rep.firstName)
        assertEquals("Lovelace", rep.lastName)
        assertEquals("Secret123", rep.credentials.single().value)
        verify(user).joinGroup(mobileGroup.id)
        verify(users, never()).delete(any())
    }

    @Test
    fun `existing email is a conflict and creates no merchant`() {
        whenever(users.create(any())) doReturn Response.status(Response.Status.CONFLICT).build()

        assertThrows<EmailConflictException> {
            service.signupMerchant("Ada", "Cafe", "ada@example.com", "Secret123", "RESTAURANT", "2026-11-08")
        }
        verify(registration, never()).register(any())
    }

    @Test
    fun `merchant setup failure removes the Keycloak user`() {
        whenever(users.create(any())) doReturn Response.created(URI("https://auth.example/users/$userId")).build()
        whenever(registration.register(any())).thenThrow(IllegalArgumentException("unknown vertical"))

        assertThrows<IllegalArgumentException> {
            service.signupMerchant("Ada", "Cafe", "ada@example.com", "Secret123", "NOPE", "2026-11-08")
        }
        verify(users).delete(userId.toString())
    }

    @Test
    fun `shopper signup records the accepted terms version`() {
        whenever(users.create(any())) doReturn Response.created(URI("https://auth.example/users/$userId")).build()

        service.signupShopper("Ada Lovelace", "ada@example.com", "Secret123", "2026-11-08")

        val consent = argumentCaptor<AccountConsent>().apply { verify(consents).save(capture()) }.firstValue
        assertEquals(userId, consent.keycloakUserId)
        assertEquals("2026-11-08", consent.termsVersion)
        inOrder(user, consents) {
            verify(user).joinGroup(mobileGroup.id)
            verify(consents).save(any())
        }
    }

    @Test
    fun `missing mobile group removes the new shopper without recording consent`() {
        whenever(users.create(any())) doReturn Response.created(URI("https://auth.example/users/$userId")).build()
        whenever(groups.groups("blitzpay-mobile-users", true, 0, 10, false)) doReturn emptyList()
        whenever(users.delete(userId.toString())) doReturn Response.noContent().build()

        assertThrows<IllegalStateException> {
            service.signupShopper("Ada", "ada@example.com", "Secret123", "2026-11-08")
        }

        verify(users).delete(userId.toString())
        verify(user, never()).joinGroup(any())
        verify(consents, never()).save(any())
    }

    @Test
    fun `existing shopper email does not assign a group or delete the existing account`() {
        whenever(users.create(any())) doReturn Response.status(Response.Status.CONFLICT).build()

        assertThrows<EmailConflictException> {
            service.signupShopper("Ada", "ada@example.com", "Secret123", "2026-11-08")
        }
        verify(user, never()).joinGroup(any())
        verify(users, never()).delete(any())
        verify(consents, never()).save(any())
    }

    @Test
    fun `assignment failure removes the new shopper`() {
        whenever(users.create(any())) doReturn Response.created(URI("https://auth.example/users/$userId")).build()
        val failure = IllegalStateException("group assignment unavailable")
        doThrow(failure).whenever(user).joinGroup(mobileGroup.id)
        whenever(users.delete(userId.toString())) doReturn Response.noContent().build()

        assertSame(failure, assertThrows<IllegalStateException> {
            service.signupShopper("Ada", "ada@example.com", "Secret123", "2026-11-08")
        })
        verify(users).delete(userId.toString())
        verify(consents, never()).save(any())
    }

    @Test
    fun `consent failure removes the new shopper`() {
        whenever(users.create(any())) doReturn Response.created(URI("https://auth.example/users/$userId")).build()
        val failure = IllegalStateException("consent unavailable")
        whenever(consents.save(any<AccountConsent>())).thenThrow(failure)
        whenever(users.delete(userId.toString())) doReturn Response.noContent().build()

        assertSame(failure, assertThrows<IllegalStateException> {
            service.signupShopper("Ada", "ada@example.com", "Secret123", "2026-11-08")
        })
        verify(users).delete(userId.toString())
    }

    @Test
    fun `assignment failure removes the new shopper and preserves the error if cleanup fails`() {
        whenever(users.create(any())) doReturn Response.created(URI("https://auth.example/users/$userId")).build()
        val failure = IllegalStateException("group assignment unavailable")
        doThrow(failure).whenever(user).joinGroup(mobileGroup.id)
        whenever(users.delete(userId.toString())).thenThrow(IllegalStateException("cleanup unavailable"))

        assertSame(failure, assertThrows<IllegalStateException> {
            service.signupShopper("Ada", "ada@example.com", "Secret123", "2026-11-08")
        })
        verify(users).delete(userId.toString())
        verify(consents, never()).save(any())
    }
}
