package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.BranchNameUpdated
import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupBody
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupClient
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupRepresentation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.util.UUID

@Service
@Profile("!contract-test")
class MerchantGroupSyncService(private val client: KeycloakGroupClient) {

    private val log = LoggerFactory.getLogger(MerchantGroupSyncService::class.java)

    fun syncMerchant(event: MerchantActivated) {
        try {
            val rootId = ensureRootGroup()
            ensureMerchantGroup(rootId, event.merchantId, event.merchantName)
            log.info("keycloak merchant group synced merchantId={}", event.merchantId)
        } catch (e: Exception) {
            log.error("keycloak merchant group sync failed merchantId={}", event.merchantId, e)
            throw e
        }
    }

    fun syncBranch(event: BranchCreated) {
        try {
            val rootId = ensureRootGroup()
            val merchantGroupId = ensureMerchantGroup(rootId, event.merchantId, event.merchantName)
            ensureBranchGroup(merchantGroupId, event.branchId, event.branchName, event.merchantId)
            log.info("keycloak branch group synced branchId={} merchantId={}", event.branchId, event.merchantId)
        } catch (e: Exception) {
            log.error("keycloak branch group sync failed branchId={} merchantId={}", event.branchId, event.merchantId, e)
            throw e
        }
    }

    fun updateMerchantName(event: MerchantNameUpdated) {
        try {
            val groups = client.searchGroups(KeycloakGroupNames.merchantGroup(event.merchantId), true)
            val group = groups.firstOrNull()
            if (group == null) {
                log.warn("keycloak merchant group not found for update merchantId={}", event.merchantId)
                return
            }
            client.updateGroup(group.id, group.withAttribute(KeycloakGroupNames.ATTR_MERCHANT_NAME, event.newName))
            log.info("keycloak merchant name updated merchantId={}", event.merchantId)
        } catch (e: Exception) {
            log.error("keycloak merchant name update failed merchantId={}", event.merchantId, e)
            throw e
        }
    }

    fun updateBranchName(event: BranchNameUpdated) {
        try {
            val merchantGroupId = findMerchantGroupId(event.merchantId)
            val children = client.getChildren(merchantGroupId, KeycloakGroupNames.branchGroup(event.branchId), true)
            val group = children.firstOrNull()
            if (group == null) {
                log.warn("keycloak branch group not found for update branchId={}", event.branchId)
                return
            }
            client.updateGroup(group.id, group.withAttribute(KeycloakGroupNames.ATTR_BRANCH_NAME, event.newName))
            log.info("keycloak branch name updated branchId={}", event.branchId)
        } catch (e: Exception) {
            log.error("keycloak branch name update failed branchId={}", event.branchId, e)
            throw e
        }
    }

    private fun ensureRootGroup(): String {
        val groups = client.searchGroups(KeycloakGroupNames.ROOT, true)
        if (groups.isNotEmpty()) return groups.first().id
        return try {
            client.createGroup(KeycloakGroupBody(KeycloakGroupNames.ROOT)).extractId()
        } catch (_: HttpClientErrorException.Conflict) {
            client.searchGroups(KeycloakGroupNames.ROOT, true).first().id
        }
    }

    private fun ensureMerchantGroup(rootId: String, merchantId: UUID, merchantName: String): String =
        ensureChildGroup(rootId, KeycloakGroupNames.merchantGroup(merchantId), merchantAttributes(merchantId, merchantName))

    private fun ensureBranchGroup(merchantGroupId: String, branchId: UUID, branchName: String, merchantId: UUID): String =
        ensureChildGroup(merchantGroupId, KeycloakGroupNames.branchGroup(branchId), branchAttributes(branchId, branchName, merchantId))

    private fun ensureChildGroup(parentId: String, groupName: String, attributes: Map<String, List<String>>): String {
        val children = client.getChildren(parentId, groupName, true)
        if (children.isNotEmpty()) {
            val existing = children.first()
            client.updateGroup(existing.id, KeycloakGroupBody(existing.name, attributes))
            return existing.id
        }
        return try {
            client.createChildGroup(parentId, KeycloakGroupBody(groupName, attributes)).extractId()
        } catch (_: HttpClientErrorException.Conflict) {
            client.getChildren(parentId, groupName, true).first().id
        }
    }

    private fun findMerchantGroupId(merchantId: UUID): String {
        val rootId = ensureRootGroup()
        return client.getChildren(rootId, KeycloakGroupNames.merchantGroup(merchantId), true)
            .firstOrNull()?.id
            ?: throw NoSuchElementException("merchant group not found: $merchantId")
    }

    private fun merchantAttributes(merchantId: UUID, merchantName: String) = mapOf(
        KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
        KeycloakGroupNames.ATTR_MERCHANT_NAME to listOf(merchantName),
    )

    private fun branchAttributes(branchId: UUID, branchName: String, merchantId: UUID) = mapOf(
        KeycloakGroupNames.ATTR_BRANCH_ID to listOf(branchId.toString()),
        KeycloakGroupNames.ATTR_BRANCH_NAME to listOf(branchName),
        KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
    )

    private fun ResponseEntity<Void>.extractId(): String =
        headers.location?.path?.substringAfterLast("/")
            ?: throw IllegalStateException("No Location header in Keycloak group creation response")

    private fun KeycloakGroupRepresentation.withAttribute(key: String, value: String): KeycloakGroupBody =
        KeycloakGroupBody(name, (attributes ?: emptyMap()) + mapOf(key to listOf(value)))
}
