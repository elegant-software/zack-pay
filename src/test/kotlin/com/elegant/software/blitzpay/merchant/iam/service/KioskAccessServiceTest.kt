package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.config.KioskAccessProperties
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKiosk
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientsResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KioskAccessServiceTest {
    private val kiosks = mock<MerchantKioskRepository>()
    private val keycloak = mock<Keycloak>()
    private val realm = mock<RealmResource>()
    private val clients = mock<ClientsResource>()
    private val users = mock<UsersResource>()
    private val user = mock<UserResource>()
    private val groupSync = mock<MerchantGroupSyncService>()
    private val merchantRepository = mock<MerchantRepository>()

    private val service = KioskAccessService(
        kiosks, keycloak, KeycloakProperties("https://auth.example", "blitzpay", "admin", "secret"),
        KioskAccessProperties(apiBaseUrl = "https://api.example"), groupSync, merchantRepository, KitchenPasswordGenerator(),
    )

    private val merchantId = UUID.randomUUID()
    private val branch = MerchantBranch(merchantId = merchantId, name = "Altstadt", branchCode = "BR-1A2B3C4D5E6F")

    private fun response(status: Int, location: String? = null): Response = mock {
        on { this.status } doReturn status
        on { getHeaderString("Location") } doReturn location
    }

    @BeforeEach
    fun setUp() {
        whenever(keycloak.realm("blitzpay")).thenReturn(realm)
        whenever(realm.clients()).thenReturn(clients)
        whenever(realm.users()).thenReturn(users)
        // The kiosk client exists already
        whenever(clients.findByClientId("blitzpay-kiosk")).thenReturn(listOf(ClientRepresentation().apply { clientId = "blitzpay-kiosk" }))
        whenever(users.create(any())).thenAnswer { response(201, "https://auth.example/users/user-id") }
        whenever(users.get("user-id")).thenReturn(user)
        whenever(user.groups()).thenReturn(emptyList())
        whenever(merchantRepository.findById(merchantId)).thenReturn(
            Optional.of(Merchant(id = merchantId, merchantName = "Pizza GmbH", businessType = "RESTAURANT", operatingCountry = "DE", merchantStatus = MerchantOperationalStatus.ACTIVE)),
        )
        whenever(groupSync.ensureBranchGroupExists(merchantId, "Pizza GmbH", branch.id, "Altstadt")).thenReturn("branch-group-id")
        whenever(kiosks.save(any<MerchantKiosk>())).thenAnswer { it.arguments[0] }
    }

    private fun storedUser(username: String) {
        whenever(user.toRepresentation()).thenReturn(UserRepresentation().apply { id = "user-id"; this.username = username; isEnabled = true })
    }

    @Test
    fun `adding a kiosk numbers it after the last one and provisions its own Keycloak user in the branch group`() {
        whenever(kiosks.findTopByBranchIdOrderByNumberDesc(branch.id)).thenReturn(
            MerchantKiosk(branchId = branch.id, number = 2, name = "Kiosk 2", username = "kiosk-br-1a2b3c4d5e6f-2"),
        )
        storedUser("kiosk-br-1a2b3c4d5e6f-3")

        val login = service.create(branch, "  ")

        assertEquals("kiosk-br-1a2b3c4d5e6f-3", login.username)
        assertEquals("Kiosk 3", login.name)
        assertEquals("blitzpay-kiosk", login.clientId)
        assertEquals("https://api.example", login.apiBaseUrl)
        assertEquals(branch.id, login.branchId)
        assertTrue(login.password.length >= 32)
        assertFalse(login.toString().contains(login.password))
        val created = argumentCaptor<UserRepresentation>().also { verify(users).create(it.capture()) }.firstValue
        assertEquals("kiosk-br-1a2b3c4d5e6f-3", created.username)
        assertEquals("kiosk-br-1a2b3c4d5e6f-3@kiosk.invalid", created.email)
        verify(user).joinGroup("branch-group-id")
        verify(user).resetPassword(any())
        // The client already existed, so nothing was created
        verify(clients, never()).create(any())
    }

    @Test
    fun `the first kiosk of a branch is number 1 and keeps its given name`() {
        storedUser("kiosk-br-1a2b3c4d5e6f-1")

        val login = service.create(branch, "Entrance")

        assertEquals("kiosk-br-1a2b3c4d5e6f-1", login.username)
        assertEquals("Entrance", login.name)
    }

    @Test
    fun `deleting a kiosk removes its Keycloak user and its row`() {
        val kiosk = MerchantKiosk(branchId = branch.id, number = 1, name = "Kiosk 1", username = "kiosk-br-1a2b3c4d5e6f-1")
        whenever(kiosks.findByIdAndBranchId(kiosk.id, branch.id)).thenReturn(kiosk)
        whenever(users.searchByUsername(kiosk.username, true)).thenReturn(listOf(UserRepresentation().apply { id = "user-id"; username = kiosk.username }))
        whenever(users.delete("user-id")).thenAnswer { response(204) }

        service.delete(branch, kiosk.id)

        verify(users).delete("user-id")
        verify(kiosks).delete(kiosk)
    }

    @Test
    fun `a kiosk of another branch is not found`() {
        val id = UUID.randomUUID()
        assertFailsWith<NoSuchElementException> { service.enable(branch, id) }
        assertFailsWith<NoSuchElementException> { service.disable(branch, id) }
        assertFailsWith<NoSuchElementException> { service.delete(branch, id) }
    }

    @Test
    fun `kiosk usernames come from the branch code and the kiosk number`() {
        assertEquals("kiosk-br-1a2b3c-2", KioskAccessService.kioskUsername("BR-1A2B3C", 2))
        assertEquals("kiosk-main-st-1", KioskAccessService.kioskUsername(" Main St! ", 1))
    }
}
