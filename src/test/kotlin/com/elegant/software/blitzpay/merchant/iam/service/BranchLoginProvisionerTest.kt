package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.config.KioskAccessProperties
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import org.keycloak.representations.idm.ProtocolMapperRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class BranchLoginProvisionerTest {
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

    private val merchantId = UUID.randomUUID()
    private val branch = MerchantBranch(merchantId = merchantId, name = "Altstadt", branchCode = "BR-1A2B3C4D5E6F")

    private val provisioner = BranchLoginProvisioner(
        keycloak, KeycloakProperties("https://auth.example", "blitzpay", "admin", "secret"), KioskAccessProperties(),
        "BlitzPay Kiosk", "Kiosk Login", groupSync, merchantRepository, KitchenPasswordGenerator(), LoggerFactory.getLogger("test"),
    )

    private fun response(status: Int, location: String? = null): Response = mock {
        on { this.status } doReturn status
        on { getHeaderString("Location") } doReturn location
    }

    @BeforeEach
    fun setUp() {
        whenever(keycloak.realm("blitzpay")).thenReturn(realm)
        whenever(realm.clients()).thenReturn(clients)
        whenever(realm.users()).thenReturn(users)
        whenever(realm.clientScopes()).thenReturn(clientScopes)
        whenever(clients.get("client-id")).thenReturn(client)
        whenever(client.protocolMappers).thenReturn(mappers)
        whenever(client.optionalClientScopes).thenReturn(emptyList())
        whenever(client.defaultClientScopes).thenReturn(emptyList())
        whenever(clientScopes.findAll()).thenReturn(listOf(ClientScopeRepresentation().apply { id = "offline-id"; name = "offline_access" }))
        whenever(mappers.createMapper(any<ProtocolMapperRepresentation>())).thenAnswer { response(201) }
        whenever(users.create(any())).thenAnswer { response(201, "https://auth.example/users/user-id") }
        whenever(users.get("user-id")).thenReturn(user)
        whenever(user.groups()).thenReturn(emptyList())
        whenever(user.toRepresentation()).thenReturn(UserRepresentation().apply { id = "user-id"; username = "altstadt-kiosk-1"; isEnabled = true })
        whenever(merchantRepository.findById(merchantId)).thenReturn(
            Optional.of(Merchant(id = merchantId, merchantName = "Pizza GmbH", businessType = "RESTAURANT", operatingCountry = "DE", merchantStatus = MerchantOperationalStatus.ACTIVE)),
        )
        whenever(groupSync.ensureBranchGroupExists(merchantId, "Pizza GmbH", branch.id, "Altstadt")).thenReturn("branch-group-id")
    }

    private fun clientExists() {
        whenever(clients.findByClientId("blitzpay-kiosk")).thenReturn(listOf(ClientRepresentation().apply { clientId = "blitzpay-kiosk" }))
    }

    private fun clientMissing() {
        whenever(clients.findByClientId("blitzpay-kiosk")).thenReturn(emptyList())
        whenever(clients.create(any())).thenAnswer { response(201, "https://auth.example/clients/client-id") }
        whenever(mappers.mappers).thenReturn(emptyList())
    }

    @Test
    fun `a new login user gets the merchant_id attribute`() {
        clientExists()

        provisioner.enable(branch, "altstadt-kiosk-1", "Kiosk", "1")

        val created = argumentCaptor<UserRepresentation>().also { verify(users).create(it.capture()) }.firstValue
        assertEquals(mapOf("merchant_id" to listOf(merchantId.toString())), created.attributes)
    }

    @Test
    fun `re-enabling an existing user adds merchant_id and keeps its other attributes`() {
        clientExists()
        val existing = UserRepresentation().apply { id = "user-id"; username = "altstadt-kiosk-1"; attributes = mapOf("other" to listOf("x")) }
        whenever(users.searchByUsername("altstadt-kiosk-1", true)).thenReturn(listOf(existing))
        whenever(user.toRepresentation()).thenReturn(existing)

        provisioner.enable(branch, "altstadt-kiosk-1", "Kiosk", "1")

        val updated = argumentCaptor<UserRepresentation>().also { verify(user).update(it.capture()) }.firstValue
        assertEquals(mapOf("other" to listOf("x"), "merchant_id" to listOf(merchantId.toString())), updated.attributes)
        verify(users, never()).create(any())
    }

    @Test
    fun `a new client gets the groups mapper unchanged and a merchant_id user attribute mapper`() {
        clientMissing()

        provisioner.enable(branch, "altstadt-kiosk-1", "Kiosk", "1")

        val created = argumentCaptor<ProtocolMapperRepresentation>().also { verify(mappers, times(2)).createMapper(it.capture()) }.allValues
        assertEquals("false", created.single { it.name == "groups" }.config["full.path"])
        val merchant = created.single { it.name == "merchant_id" }
        assertEquals("oidc-usermodel-attribute-mapper", merchant.protocolMapper)
        assertEquals("openid-connect", merchant.protocol)
        assertEquals(
            mapOf(
                "user.attribute" to "merchant_id", "claim.name" to "merchant_id", "jsonType.label" to "String",
                "access.token.claim" to "true", "id.token.claim" to "true",
                "userinfo.token.claim" to "true", "introspection.token.claim" to "true",
            ),
            merchant.config,
        )
    }

    @Test
    fun `an existing merchant_id mapper is updated in place instead of duplicated`() {
        clientMissing()
        whenever(mappers.mappers).thenReturn(
            listOf(ProtocolMapperRepresentation().apply { id = "mapper-id"; name = "merchant_id"; protocolMapper = "oidc-usermodel-attribute-mapper" }),
        )

        provisioner.enable(branch, "altstadt-kiosk-1", "Kiosk", "1")

        val updated = argumentCaptor<ProtocolMapperRepresentation>().also { verify(mappers).update(eq("mapper-id"), it.capture()) }.firstValue
        assertEquals("mapper-id", updated.id)
        assertEquals("merchant_id", updated.config["claim.name"])
        // Only the groups mapper is created
        verify(mappers).createMapper(any<ProtocolMapperRepresentation>())
    }
}
