package com.elegant.software.blitzpay.merchant.iam.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.config.KitchenAccessProperties
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.ClientScopesResource
import org.keycloak.admin.client.resource.ClientsResource
import org.keycloak.admin.client.resource.ProtocolMappersResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.ProtocolMapperRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KitchenAccessServiceTest {

    private val keycloak = mock<Keycloak>()
    private val realm = mock<RealmResource>()
    private val clients = mock<ClientsResource>()
    private val client = mock<ClientResource>()
    private val mappers = mock<ProtocolMappersResource>()
    private val clientScopes = mock<ClientScopesResource>()
    private val users = mock<UsersResource>()
    private val user = mock<UserResource>()
    private val groupSync = mock<MerchantGroupSyncService>()
    private val merchantRepository = mock<MerchantRepository>()

    private val keycloakProperties = KeycloakProperties("https://auth.example", "blitzpay", "admin", "admin-secret")
    private val kitchenProperties = KitchenAccessProperties(apiBaseUrl = "https://api.example")
    private val service = KitchenAccessService(
        keycloak, keycloakProperties, kitchenProperties, groupSync, merchantRepository, KitchenPasswordGenerator(),
    )

    private val merchantId = UUID.randomUUID()
    private val branch = MerchantBranch(merchantId = merchantId, name = "Altstadt", branchCode = "BR-1A2B3C4D5E6F")
    private val username = "altstadt-kitchen"
    private val emailUsername = "altstadt-kitchen@kitchen.invalid"
    private val clientUuid = "client-uuid"
    private val userId = "user-id"
    private val branchGroupId = "branch-group-id"
    private val offlineScope = ClientScopeRepresentation().apply { id = "offline-scope-id"; name = "offline_access" }

    private val logAppender = ListAppender<ILoggingEvent>().apply { start() }
    private val serviceLogger = LoggerFactory.getLogger(KitchenAccessService::class.java) as Logger

    @BeforeEach
    fun setUp() {
        serviceLogger.addAppender(logAppender)
        whenever(keycloak.realm("blitzpay")).thenReturn(realm)
        whenever(realm.clients()).thenReturn(clients)
        whenever(realm.clientScopes()).thenReturn(clientScopes)
        whenever(realm.users()).thenReturn(users)
        whenever(clients.get(clientUuid)).thenReturn(client)
        whenever(client.protocolMappers).thenReturn(mappers)
        whenever(mappers.mappers).thenReturn(emptyList())
        response(201).let { whenever(mappers.createMapper(any<ProtocolMapperRepresentation>())).thenReturn(it) }
        whenever(clientScopes.findAll()).thenReturn(listOf(offlineScope))
        whenever(client.optionalClientScopes).thenReturn(emptyList())
        whenever(client.defaultClientScopes).thenReturn(emptyList())
        whenever(users.get(userId)).thenReturn(user)
        whenever(user.toRepresentation()).thenReturn(UserRepresentation().apply { id = userId; username = this@KitchenAccessServiceTest.username })
        whenever(user.groups()).thenReturn(emptyList())
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant()))
        whenever(groupSync.ensureBranchGroupExists(merchantId, "Pizza GmbH", branch.id, "Altstadt")).thenReturn(branchGroupId)
    }

    @AfterEach
    fun tearDown() {
        serviceLogger.detachAppender(logAppender)
    }

    private fun merchant() = Merchant(
        id = merchantId, merchantName = "Pizza GmbH", businessType = "RESTAURANT", operatingCountry = "DE",
        merchantStatus = MerchantOperationalStatus.ACTIVE,
    )

    private fun response(status: Int, location: String? = null): Response = mock {
        on { this.status } doReturn status
        on { getHeaderString("Location") } doReturn location
    }

    private fun givenNoClient() {
        whenever(clients.findByClientId("blitzpay-kitchen")).thenReturn(emptyList())
        response(201, "https://auth.example/admin/realms/blitzpay/clients/$clientUuid").let { whenever(clients.create(any())).thenReturn(it) }
    }

    private fun givenExistingClient(rep: ClientRepresentation = existingClient()) {
        whenever(clients.findByClientId("blitzpay-kitchen")).thenReturn(listOf(rep))
    }

    private fun existingClient() = ClientRepresentation().apply {
        id = clientUuid
        clientId = "blitzpay-kitchen"
        isPublicClient = false
        isDirectAccessGrantsEnabled = true
        redirectUris = listOf("http://wrong")
        attributes = mapOf("client.offline.session.idle.timeout" to "2592000")
    }

    private fun givenNoUser() {
        whenever(users.searchByUsername(username, true)).thenReturn(emptyList())
        response(201, "https://auth.example/admin/realms/blitzpay/users/$userId").let { whenever(users.create(any())).thenReturn(it) }
    }

    private fun givenExistingUser(enabled: Boolean = true) {
        whenever(users.searchByUsername(username, true)).thenReturn(
            listOf(UserRepresentation().apply { id = userId; username = this@KitchenAccessServiceTest.username; isEnabled = enabled })
        )
    }

    private fun groupsMapper(id: String, name: String = "groups", type: String = "oidc-group-membership-mapper") =
        ProtocolMapperRepresentation().apply {
            this.id = id; this.name = name; protocolMapper = type; config = mapOf("claim.name" to "groups")
        }

    // ── realm client ────────────────────────────────────────────────────────

    @Test
    fun `creates the kitchen client with the expected configuration when missing`() {
        givenNoClient()
        givenNoUser()

        service.enable(branch)

        val captor = argumentCaptor<ClientRepresentation>()
        verify(clients).create(captor.capture())
        verify(client, never()).update(any())
        captor.firstValue.assertKitchenClientConfig()
    }

    @Test
    fun `leaves an existing kitchen client untouched`() {
        givenExistingClient()
        givenNoUser()

        service.enable(branch)

        verify(clients, never()).create(any())
        verify(clients, never()).delete(any())
        verify(clients, never()).get(clientUuid)
        verifyNoInteractions(client, mappers)
    }

    private fun ClientRepresentation.assertKitchenClientConfig() {
        assertEquals("blitzpay-kitchen", clientId)
        assertEquals(true, isEnabled)
        assertEquals(true, isPublicClient)
        assertEquals(true, isStandardFlowEnabled)
        assertEquals(false, isDirectAccessGrantsEnabled)
        assertEquals(false, isImplicitFlowEnabled)
        assertEquals(false, isServiceAccountsEnabled)
        assertEquals(listOf("app://kitchen/renderer/index.html"), redirectUris)
        assertEquals(listOf("+"), webOrigins)
        assertEquals("S256", attributes["pkce.code.challenge.method"])
        assertEquals("app://kitchen/renderer/index.html", attributes["post.logout.redirect.uris"])
    }

    @Test
    fun `creates the groups mapper when missing`() {
        givenNoClient()
        givenNoUser()

        service.enable(branch)

        val captor = argumentCaptor<ProtocolMapperRepresentation>()
        verify(mappers).createMapper(captor.capture())
        verify(mappers, never()).update(any(), any())
        with(captor.firstValue) {
            assertEquals("groups", name)
            assertEquals("oidc-group-membership-mapper", protocolMapper)
            assertEquals("groups", config["claim.name"])
            assertEquals("true", config["access.token.claim"])
            assertEquals("false", config["full.path"])
        }
    }

    @Test
    fun `updates an existing groups mapper instead of adding a duplicate`() {
        givenNoClient()
        givenNoUser()
        whenever(mappers.mappers).thenReturn(listOf(groupsMapper("mapper-1")))

        service.enable(branch)

        val captor = argumentCaptor<ProtocolMapperRepresentation>()
        verify(mappers).update(eq("mapper-1"), captor.capture())
        verify(mappers, never()).createMapper(any<ProtocolMapperRepresentation>())
        verify(mappers, never()).delete(any())
        assertEquals("mapper-1", captor.firstValue.id)
        assertEquals("true", captor.firstValue.config["access.token.claim"])
    }

    @Test
    fun `removes extra mappers emitting the groups claim and keeps one`() {
        givenNoClient()
        givenNoUser()
        whenever(mappers.mappers).thenReturn(
            listOf(
                groupsMapper("manual", name = "group membership"),
                groupsMapper("named", name = "groups"),
                groupsMapper("wrong-type", name = "groups-attr", type = "oidc-usermodel-attribute-mapper").apply {
                    config = mapOf("claim.name" to "other")
                },
            )
        )

        service.enable(branch)

        verify(mappers).delete("manual")
        verify(mappers, never()).delete("named")
        verify(mappers, never()).delete("wrong-type")
        verify(mappers).update(eq("named"), any())
        verify(mappers, never()).createMapper(any<ProtocolMapperRepresentation>())
    }

    @Test
    fun `attaches offline_access as optional client scope once`() {
        givenNoClient()
        givenNoUser()

        service.enable(branch)
        verify(client).addOptionalClientScope("offline-scope-id")

        whenever(client.optionalClientScopes).thenReturn(listOf(offlineScope))
        service.enable(branch)
        verify(client, times(1)).addOptionalClientScope("offline-scope-id")
    }

    @Test
    fun `moves offline_access from default to optional`() {
        givenNoClient()
        givenNoUser()
        whenever(client.defaultClientScopes).thenReturn(listOf(offlineScope))

        service.enable(branch)

        inOrder(client) {
            verify(client).removeDefaultClientScope("offline-scope-id")
            verify(client).addOptionalClientScope("offline-scope-id")
        }
    }

    // ── kitchen user ────────────────────────────────────────────────────────

    @Test
    fun `creates the kitchen user, joins the branch group and sets a permanent password`() {
        givenExistingClient()
        givenNoUser()

        val login = service.enable(branch)

        val created = argumentCaptor<UserRepresentation>()
        verify(users).create(created.capture())
        with(created.firstValue) {
            assertEquals(username, this.username)
            assertEquals(true, isEnabled)
            assertEquals(emptyList(), requiredActions)
            assertEquals(true, isEmailVerified)
        }
        verify(user).joinGroup(branchGroupId)
        val credential = argumentCaptor<CredentialRepresentation>()
        verify(user).resetPassword(credential.capture())
        assertEquals(CredentialRepresentation.PASSWORD, credential.firstValue.type)
        assertEquals(false, credential.firstValue.isTemporary)
        assertEquals(login.password, credential.firstValue.value)

        assertEquals(username, login.username)
        assertEquals("https://auth.example", login.keycloakUrl)
        assertEquals("blitzpay", login.realm)
        assertEquals("blitzpay-kitchen", login.clientId)
        assertEquals("https://api.example", login.apiBaseUrl)
        assertEquals(branch.id, login.branchId)
    }

    @Test
    fun `reuses and re-enables an existing kitchen user`() {
        givenExistingClient()
        givenExistingUser(enabled = false)

        service.enable(branch)

        verify(users, never()).create(any())
        val updated = argumentCaptor<UserRepresentation>()
        verify(user).update(updated.capture())
        assertEquals(true, updated.firstValue.isEnabled)
        assertEquals(emptyList(), updated.firstValue.requiredActions)
        verify(user).joinGroup(branchGroupId)
    }

    @Test
    fun `resets the password with a new strong value on every call`() {
        givenExistingClient()
        givenExistingUser()

        val first = service.enable(branch).password
        val second = service.enable(branch).password

        verify(user, times(2)).resetPassword(any())
        assertNotEquals(first, second)
        listOf(first, second).forEach {
            assertTrue(it.length >= 20, "password too short: ${it.length}")
            assertTrue(it.matches(Regex("[A-Za-z0-9_-]+")), "password not URL-safe")
        }
    }

    @Test
    fun `refuses to reuse a username bound to another branch`() {
        givenExistingClient()
        givenExistingUser()
        whenever(user.groups()).thenReturn(listOf(GroupRepresentation().apply { name = "branch_${UUID.randomUUID()}" }))

        assertThrows<BranchLoginConflictException> { service.enable(branch) }
        verify(user, never()).resetPassword(any())
    }

    @Test
    fun `password never appears in logs or toString`() {
        givenExistingClient()
        givenNoUser()

        val login = service.enable(branch)

        assertFalse(login.toString().contains(login.password))
        assertTrue(logAppender.list.isNotEmpty())
        logAppender.list.forEach { assertFalse(it.formattedMessage.contains(login.password)) }
    }

    @Test
    fun `sets the password the admin chose`() {
        givenExistingClient()
        givenExistingUser()

        val login = service.enable(branch, "test-pass-1")

        val credential = argumentCaptor<CredentialRepresentation>()
        verify(user).resetPassword(credential.capture())
        assertEquals("test-pass-1", credential.firstValue.value)
        assertEquals("test-pass-1", login.password)
    }

    @Test
    fun `refuses a too short admin password before touching Keycloak`() {
        assertThrows<BranchLoginPasswordException> { service.enable(branch, "short") }
        verify(user, never()).resetPassword(any())
    }

    @Test
    fun `username is the branch name plus kitchen`() {
        assertEquals("altstadt-kitchen", KitchenAccessService.kitchenUsername(branch))
        assertEquals("cafe-koln-main-st-kitchen", KitchenAccessService.kitchenUsername(MerchantBranch(merchantId = merchantId, name = " Café Köln — Main St! ", branchCode = branch.branchCode)))
        assertEquals("br-1a2b3c4d5e6f-kitchen", KitchenAccessService.kitchenUsername(MerchantBranch(merchantId = merchantId, name = "東京", branchCode = branch.branchCode)))
    }

    // ── disable / status ────────────────────────────────────────────────────

    @Test
    fun `disable disables the user and revokes online and offline sessions`() {
        givenExistingUser()

        val status = service.disable(branch)

        val updated = argumentCaptor<UserRepresentation>()
        inOrder(user) {
            verify(user).update(updated.capture())
            verify(user).logout()
            verify(user).revokeConsent("blitzpay-kitchen")
        }
        assertEquals(false, updated.firstValue.isEnabled)
        assertEquals(false, status.enabled)
        assertEquals(username, status.username)
    }

    @Test
    fun `disable tolerates a user without an offline token`() {
        givenExistingUser()
        doThrow(NotFoundException()).whenever(user).revokeConsent("blitzpay-kitchen")

        assertEquals(false, service.disable(branch).enabled)
    }

    @Test
    fun `disable without a kitchen user is a no-op`() {
        whenever(users.searchByUsername(username, true)).thenReturn(emptyList())

        val status = service.disable(branch)

        assertEquals(false, status.enabled)
        assertNull(status.username)
        verify(users, never()).get(any())
    }

    // Realms with "Email as username" store the email as username, so an exact username search misses the user.
    private fun givenUserStoredUnderEmail(enabled: Boolean = true) {
        whenever(users.searchByUsername(username, true)).thenReturn(emptyList())
        whenever(users.searchByEmail(emailUsername, true)).thenReturn(
            listOf(UserRepresentation().apply { id = userId; this.username = emailUsername; isEnabled = enabled })
        )
    }

    @Test
    fun `status finds a kitchen user stored under its email`() {
        givenUserStoredUnderEmail()

        val status = service.status(branch)

        assertEquals(true, status.enabled)
        assertEquals(emailUsername, status.username)
    }

    @Test
    fun `enable reuses a kitchen user stored under its email and returns the stored username`() {
        givenExistingClient()
        givenUserStoredUnderEmail(enabled = false)
        // Keycloak returns a fresh representation per call.
        whenever(user.toRepresentation()).thenAnswer { UserRepresentation().apply { id = userId; this.username = emailUsername } }

        val login = service.enable(branch)

        verify(users, never()).create(any())
        assertEquals(emailUsername, login.username)
    }

    @Test
    fun `status reports the kitchen user`() {
        givenExistingUser(enabled = true)
        assertEquals(true, service.status(branch).enabled)
        assertEquals(username, service.status(branch).username)

        whenever(users.searchByUsername(username, true)).thenReturn(emptyList())
        assertEquals(false, service.status(branch).enabled)
        assertNull(service.status(branch).username)
    }
}
