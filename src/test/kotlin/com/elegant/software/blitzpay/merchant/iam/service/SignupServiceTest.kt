package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.domain.AccountConsent
import com.elegant.software.blitzpay.merchant.iam.repository.AccountConsentRepository
import com.elegant.software.blitzpay.merchant.service.MerchantBranchService
import com.elegant.software.blitzpay.merchant.service.MerchantRegistrationService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.UserRepresentation
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

class SignupServiceTest {

    private val keycloak = mock<Keycloak>()
    private val realm = mock<RealmResource>()
    private val users = mock<UsersResource>()
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
    }
}
