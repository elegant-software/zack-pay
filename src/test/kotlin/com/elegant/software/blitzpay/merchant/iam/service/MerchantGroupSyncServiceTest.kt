package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.BranchNameUpdated
import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupBody
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupClient
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupRepresentation
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import java.util.UUID
import kotlin.test.assertEquals

class MerchantGroupSyncServiceTest {

    private val client = mock<KeycloakGroupClient>()
    private val service = MerchantGroupSyncService(client)

    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val rootGroupId = "root-id"
    private val merchantGroupId = "merchant-group-id"
    private val branchGroupId = "branch-group-id"

    private fun rootGroup() = KeycloakGroupRepresentation(id = rootGroupId, name = KeycloakGroupNames.ROOT)
    private fun merchantGroup() = KeycloakGroupRepresentation(
        id = merchantGroupId,
        name = KeycloakGroupNames.merchantGroup(merchantId),
        attributes = mapOf(
            KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
            KeycloakGroupNames.ATTR_MERCHANT_NAME to listOf("Acme GmbH"),
        )
    )
    private fun branchGroup() = KeycloakGroupRepresentation(
        id = branchGroupId,
        name = KeycloakGroupNames.branchGroup(branchId),
        attributes = mapOf(
            KeycloakGroupNames.ATTR_BRANCH_ID to listOf(branchId.toString()),
            KeycloakGroupNames.ATTR_BRANCH_NAME to listOf("Main Branch"),
            KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
        )
    )

    private fun stubRootExists() {
        whenever(client.searchGroups(KeycloakGroupNames.ROOT, true)).thenReturn(listOf(rootGroup()))
    }

    private fun stubMerchantGroupExists() {
        whenever(client.getChildren(eq(rootGroupId), eq(KeycloakGroupNames.merchantGroup(merchantId)), eq(true)))
            .thenReturn(listOf(merchantGroup()))
    }

    private fun createdResponse(groupId: String): ResponseEntity<Void> =
        ResponseEntity.created(java.net.URI.create("/groups/$groupId")).build()

    @Test
    fun `syncMerchant creates root group and merchant group when neither exists`() {
        whenever(client.searchGroups(KeycloakGroupNames.ROOT, true)).thenReturn(emptyList())
        whenever(client.createGroup(KeycloakGroupBody(KeycloakGroupNames.ROOT))).thenReturn(createdResponse(rootGroupId))
        whenever(client.getChildren(eq(rootGroupId), eq(KeycloakGroupNames.merchantGroup(merchantId)), eq(true))).thenReturn(emptyList())
        whenever(client.createChildGroup(eq(rootGroupId), any())).thenReturn(createdResponse(merchantGroupId))

        service.syncMerchant(MerchantActivated(merchantId, "Acme GmbH"))

        verify(client).createGroup(KeycloakGroupBody(KeycloakGroupNames.ROOT))
        val bodyCaptor = argumentCaptor<KeycloakGroupBody>()
        verify(client).createChildGroup(eq(rootGroupId), bodyCaptor.capture())
        assertEquals(KeycloakGroupNames.merchantGroup(merchantId), bodyCaptor.firstValue.name)
        assertEquals(listOf(merchantId.toString()), bodyCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_ID])
        assertEquals(listOf("Acme GmbH"), bodyCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_NAME])
    }

    @Test
    fun `syncMerchant reuses existing root group`() {
        stubRootExists()
        whenever(client.getChildren(eq(rootGroupId), eq(KeycloakGroupNames.merchantGroup(merchantId)), eq(true))).thenReturn(emptyList())
        whenever(client.createChildGroup(eq(rootGroupId), any())).thenReturn(createdResponse(merchantGroupId))

        service.syncMerchant(MerchantActivated(merchantId, "Acme GmbH"))

        verify(client, never()).createGroup(any())
    }

    @Test
    fun `syncMerchant updates attributes when merchant group already exists`() {
        stubRootExists()
        stubMerchantGroupExists()

        service.syncMerchant(MerchantActivated(merchantId, "Acme GmbH Updated"))

        val bodyCaptor = argumentCaptor<KeycloakGroupBody>()
        verify(client).updateGroup(eq(merchantGroupId), bodyCaptor.capture())
        assertEquals(listOf("Acme GmbH Updated"), bodyCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_NAME])
    }

    @Test
    fun `syncBranch creates branch group under existing merchant group`() {
        stubRootExists()
        stubMerchantGroupExists()
        whenever(client.getChildren(eq(merchantGroupId), eq(KeycloakGroupNames.branchGroup(branchId)), eq(true))).thenReturn(emptyList())
        whenever(client.createChildGroup(eq(merchantGroupId), any())).thenReturn(createdResponse(branchGroupId))

        service.syncBranch(BranchCreated(branchId, merchantId, "Main Branch", "Acme GmbH"))

        val bodyCaptor = argumentCaptor<KeycloakGroupBody>()
        verify(client).createChildGroup(eq(merchantGroupId), bodyCaptor.capture())
        assertEquals(KeycloakGroupNames.branchGroup(branchId), bodyCaptor.firstValue.name)
        assertEquals(listOf(branchId.toString()), bodyCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_BRANCH_ID])
        assertEquals(listOf("Main Branch"), bodyCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_BRANCH_NAME])
        assertEquals(listOf(merchantId.toString()), bodyCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_ID])
    }

    @Test
    fun `syncBranch updates attributes when branch group already exists`() {
        stubRootExists()
        stubMerchantGroupExists()
        whenever(client.getChildren(eq(merchantGroupId), eq(KeycloakGroupNames.branchGroup(branchId)), eq(true)))
            .thenReturn(listOf(branchGroup()))

        service.syncBranch(BranchCreated(branchId, merchantId, "Main Branch", "Acme GmbH"))

        verify(client).updateGroup(eq(branchGroupId), any())
    }

    @Test
    fun `updateMerchantName updates merchant_name attribute`() {
        whenever(client.searchGroups(KeycloakGroupNames.merchantGroup(merchantId), true))
            .thenReturn(listOf(merchantGroup()))

        service.updateMerchantName(MerchantNameUpdated(merchantId, "New Name GmbH"))

        val bodyCaptor = argumentCaptor<KeycloakGroupBody>()
        verify(client).updateGroup(eq(merchantGroupId), bodyCaptor.capture())
        assertEquals(listOf("New Name GmbH"), bodyCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_MERCHANT_NAME])
    }

    @Test
    fun `updateMerchantName is a no-op when merchant group not found`() {
        whenever(client.searchGroups(KeycloakGroupNames.merchantGroup(merchantId), true)).thenReturn(emptyList())

        service.updateMerchantName(MerchantNameUpdated(merchantId, "New Name GmbH"))

        verify(client, never()).updateGroup(any(), any())
    }

    @Test
    fun `updateBranchName updates branch_name attribute`() {
        stubRootExists()
        stubMerchantGroupExists()
        whenever(client.getChildren(eq(merchantGroupId), eq(KeycloakGroupNames.branchGroup(branchId)), eq(true)))
            .thenReturn(listOf(branchGroup()))

        service.updateBranchName(BranchNameUpdated(branchId, merchantId, "New Branch Name"))

        val bodyCaptor = argumentCaptor<KeycloakGroupBody>()
        verify(client).updateGroup(eq(branchGroupId), bodyCaptor.capture())
        assertEquals(listOf("New Branch Name"), bodyCaptor.firstValue.attributes[KeycloakGroupNames.ATTR_BRANCH_NAME])
    }

    @Test
    fun `updateBranchName is a no-op when branch group not found`() {
        stubRootExists()
        stubMerchantGroupExists()
        whenever(client.getChildren(eq(merchantGroupId), eq(KeycloakGroupNames.branchGroup(branchId)), eq(true)))
            .thenReturn(emptyList())

        service.updateBranchName(BranchNameUpdated(branchId, merchantId, "New Branch Name"))

        verify(client, never()).updateGroup(any(), any())
    }
}
