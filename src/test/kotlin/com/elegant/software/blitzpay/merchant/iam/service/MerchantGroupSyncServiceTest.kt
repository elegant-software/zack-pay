package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.BranchNameUpdated
import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.GroupResource
import org.keycloak.admin.client.resource.GroupsResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.GroupRepresentation
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals

class MerchantGroupSyncServiceTest {

    private val keycloak = mock<Keycloak>()
    private val realmResource = mock<RealmResource>()
    private val groupsResource = mock<GroupsResource>()
    private val properties = KeycloakProperties(
        serverUrl = "http://keycloak", realm = "test", adminClientId = "admin", adminClientSecret = "secret"
    )
    private val merchantRepository = mock<com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository>()
    private val branchRepository = mock<com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository>()
    private val service = MerchantGroupSyncService(keycloak, properties, merchantRepository, branchRepository)

    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val rootGroupId = "root-id"
    private val merchantGroupId = "merchant-group-id"
    private val branchGroupId = "branch-group-id"

    @BeforeEach
    fun setUp() {
        whenever(keycloak.realm(properties.realm)).thenReturn(realmResource)
        whenever(realmResource.groups()).thenReturn(groupsResource)
    }

    private fun group(id: String, name: String, attributes: Map<String, List<String>> = emptyMap()) =
        GroupRepresentation().apply { this.id = id; this.name = name; this.attributes = attributes }

    private fun createdResponse(groupId: String): Response {
        val response = mock<Response>()
        whenever(response.status).thenReturn(201)
        whenever(response.getHeaderString("Location")).thenReturn("http://keycloak/admin/realms/test/groups/$groupId")
        return response
    }

    private fun groupResource(parentId: String): GroupResource =
        mock<GroupResource>().also { whenever(groupsResource.group(parentId)).thenReturn(it) }

    private fun stubRootExists() {
        whenever(groupsResource.groups(KeycloakGroupNames.ROOT, true, 0, 10, false))
            .thenReturn(listOf(group(rootGroupId, KeycloakGroupNames.ROOT)))
    }

    private fun stubMerchantGroupExists() {
        val rootResource = groupResource(rootGroupId)
        whenever(rootResource.getSubGroups(KeycloakGroupNames.merchantGroup(merchantId), true, 0, 200, false))
            .thenReturn(listOf(group(merchantGroupId, KeycloakGroupNames.merchantGroup(merchantId))))
    }

    @Test
    fun `syncMerchant creates root group and merchant group when neither exists`() {
        val addRootResponse = createdResponse(rootGroupId)
        val addMerchantResponse = createdResponse(merchantGroupId)
        whenever(groupsResource.groups(KeycloakGroupNames.ROOT, true, 0, 10, false)).thenReturn(emptyList())
        whenever(groupsResource.add(any())).thenReturn(addRootResponse)

        val rootResource = groupResource(rootGroupId)
        whenever(rootResource.getSubGroups(KeycloakGroupNames.merchantGroup(merchantId), true, 0, 200, false)).thenReturn(emptyList())
        whenever(rootResource.subGroup(any())).thenReturn(addMerchantResponse)

        service.syncMerchant(MerchantActivated(merchantId, "Acme GmbH"))

        val groupCaptor = argumentCaptor<GroupRepresentation>()
        verify(groupsResource).add(groupCaptor.capture())
        assertEquals(KeycloakGroupNames.ROOT, groupCaptor.firstValue.name)

        val childCaptor = argumentCaptor<GroupRepresentation>()
        verify(rootResource).subGroup(childCaptor.capture())
        assertEquals(KeycloakGroupNames.merchantGroup(merchantId), childCaptor.firstValue.name)
        assertEquals(listOf(merchantId.toString()), childCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_ID])
        assertEquals(listOf("Acme GmbH"), childCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_NAME])
    }

    @Test
    fun `syncMerchant reuses existing root group`() {
        val addMerchantResponse = createdResponse(merchantGroupId)
        stubRootExists()
        val rootResource = groupResource(rootGroupId)
        whenever(rootResource.getSubGroups(KeycloakGroupNames.merchantGroup(merchantId), true, 0, 200, false)).thenReturn(emptyList())
        whenever(rootResource.subGroup(any())).thenReturn(addMerchantResponse)

        service.syncMerchant(MerchantActivated(merchantId, "Acme GmbH"))

        verify(groupsResource, never()).add(any())
    }

    @Test
    fun `syncMerchant updates attributes when merchant group already exists`() {
        stubRootExists()
        stubMerchantGroupExists()
        val merchantResource = groupResource(merchantGroupId)

        service.syncMerchant(MerchantActivated(merchantId, "Acme GmbH Updated"))

        val captor = argumentCaptor<GroupRepresentation>()
        verify(merchantResource).update(captor.capture())
        assertEquals(listOf("Acme GmbH Updated"), captor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_NAME])
    }

    @Test
    fun `syncBranch creates branch group under existing merchant group`() {
        val addBranchResponse = createdResponse(branchGroupId)
        stubRootExists()
        stubMerchantGroupExists()
        val merchantResource = groupResource(merchantGroupId)
        whenever(merchantResource.getSubGroups(KeycloakGroupNames.branchGroup(branchId), true, 0, 200, false)).thenReturn(emptyList())
        whenever(merchantResource.subGroup(any())).thenReturn(addBranchResponse)

        service.syncBranch(BranchCreated(branchId, merchantId, "Main Branch", "Acme GmbH"))

        val captor = argumentCaptor<GroupRepresentation>()
        verify(merchantResource).subGroup(captor.capture())
        assertEquals(KeycloakGroupNames.branchGroup(branchId), captor.firstValue.name)
        assertEquals(listOf(branchId.toString()), captor.firstValue.attributes[KeycloakGroupNames.ATTR_BRANCH_ID])
        assertEquals(listOf("Main Branch"), captor.firstValue.attributes[KeycloakGroupNames.ATTR_BRANCH_NAME])
        assertEquals(listOf(merchantId.toString()), captor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_ID])
    }

    @Test
    fun `syncBranch updates attributes when branch group already exists`() {
        stubRootExists()
        stubMerchantGroupExists()
        val merchantResource = groupResource(merchantGroupId)
        whenever(merchantResource.getSubGroups(KeycloakGroupNames.branchGroup(branchId), true, 0, 200, false))
            .thenReturn(listOf(group(branchGroupId, KeycloakGroupNames.branchGroup(branchId))))
        val branchResource = groupResource(branchGroupId)

        service.syncBranch(BranchCreated(branchId, merchantId, "Main Branch", "Acme GmbH"))

        verify(branchResource).update(any())
    }

    @Test
    fun `updateMerchantName updates merchant_name attribute`() {
        whenever(groupsResource.groups(KeycloakGroupNames.merchantGroup(merchantId), true, 0, 10, false))
            .thenReturn(listOf(group(merchantGroupId, KeycloakGroupNames.merchantGroup(merchantId),
                mapOf(KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
                    KeycloakGroupNames.ATTR_MERCHANT_NAME to listOf("Acme GmbH")))))
        val merchantResource = groupResource(merchantGroupId)

        service.updateMerchantName(MerchantNameUpdated(merchantId, "New Name GmbH"))

        val captor = argumentCaptor<GroupRepresentation>()
        verify(merchantResource).update(captor.capture())
        assertEquals(listOf("New Name GmbH"), captor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_NAME])
    }

    @Test
    fun `updateMerchantName is a no-op when merchant group not found`() {
        whenever(groupsResource.groups(KeycloakGroupNames.merchantGroup(merchantId), true, 0, 10, false))
            .thenReturn(emptyList())

        service.updateMerchantName(MerchantNameUpdated(merchantId, "New Name GmbH"))

        verify(groupsResource, never()).group(any())
    }

    @Test
    fun `updateBranchName updates branch_name attribute`() {
        stubRootExists()
        stubMerchantGroupExists()
        val merchantResource = groupResource(merchantGroupId)
        whenever(merchantResource.getSubGroups(KeycloakGroupNames.branchGroup(branchId), true, 0, 200, false))
            .thenReturn(listOf(group(branchGroupId, KeycloakGroupNames.branchGroup(branchId),
                mapOf(KeycloakGroupNames.ATTR_BRANCH_NAME to listOf("Old Name")))))
        val branchResource = groupResource(branchGroupId)

        service.updateBranchName(BranchNameUpdated(branchId, merchantId, "New Branch Name"))

        val captor = argumentCaptor<GroupRepresentation>()
        verify(branchResource).update(captor.capture())
        assertEquals(listOf("New Branch Name"), captor.firstValue.attributes[KeycloakGroupNames.ATTR_BRANCH_NAME])
    }

    @Test
    fun `updateBranchName is a no-op when branch group not found`() {
        stubRootExists()
        stubMerchantGroupExists()
        val merchantResource = groupResource(merchantGroupId)
        whenever(merchantResource.getSubGroups(KeycloakGroupNames.branchGroup(branchId), true, 0, 200, false))
            .thenReturn(emptyList())

        service.updateBranchName(BranchNameUpdated(branchId, merchantId, "New Branch Name"))

        verify(merchantResource, never()).update(any())
    }
}
