package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.BranchNameUpdated
import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.GroupsResource
import org.keycloak.representations.idm.GroupRepresentation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.UUID

@Service
@Profile("!contract-test")
class MerchantGroupSyncService(
    private val keycloak: Keycloak,
    private val properties: KeycloakProperties,
    private val merchantRepository: MerchantRepository,
    private val branchRepository: MerchantBranchRepository,
) {
    private val log = LoggerFactory.getLogger(MerchantGroupSyncService::class.java)

    fun ensureMerchantGroupExists(merchantId: UUID, merchantName: String) {
        val rootId = ensureRootGroup()
        ensureMerchantGroup(rootId, merchantId, merchantName)
        log.info("keycloak merchant group ensured merchantId={}", merchantId)
    }

    fun ensureBranchGroupExists(merchantId: UUID, merchantName: String, branchId: UUID, branchName: String) {
        val rootId = ensureRootGroup()
        val merchantGroupId = ensureMerchantGroup(rootId, merchantId, merchantName)
        ensureBranchGroup(merchantGroupId, branchId, branchName, merchantId)
        log.info("keycloak branch group ensured branchId={} merchantId={}", branchId, merchantId)
    }

    fun syncAll(): SyncResult {
        val merchants = merchantRepository.findAllByMerchantStatus(MerchantOperationalStatus.ACTIVE)
        var merchantsSynced = 0
        var branchesSynced = 0
        val failures = mutableListOf<String>()

        for (merchant in merchants) {
            try {
                ensureMerchantGroupExists(merchant.id, merchant.merchantName)
                merchantsSynced++
            } catch (e: Exception) {
                log.error("sync failed for merchant {}", merchant.id, e)
                failures += "merchant ${merchant.id}: ${e.message}"
                continue
            }

            val branches = branchRepository.findAllByMerchantIdAndActiveTrue(merchant.id)
            for (branch in branches) {
                try {
                    ensureBranchGroupExists(merchant.id, merchant.merchantName, branch.id, branch.name)
                    branchesSynced++
                } catch (e: Exception) {
                    log.error("sync failed for branch {} merchant {}", branch.id, merchant.id, e)
                    failures += "branch ${branch.id} (merchant ${merchant.id}): ${e.message}"
                }
            }
        }

        log.info("keycloak sync complete: merchants={} branches={} failures={}", merchantsSynced, branchesSynced, failures.size)
        return SyncResult(merchantsSynced, branchesSynced, failures)
    }

    data class SyncResult(val merchantsSynced: Int, val branchesSynced: Int, val failures: List<String>)

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
            val group = groups().findByName(KeycloakGroupNames.merchantGroup(event.merchantId))
            if (group == null) {
                log.warn("keycloak merchant group not found for update merchantId={}", event.merchantId)
                return
            }
            group.attributes = (group.attributes ?: emptyMap()) + mapOf(KeycloakGroupNames.ATTR_MERCHANT_NAME to listOf(event.newName))
            groups().group(group.id).update(group)
            log.info("keycloak merchant name updated merchantId={}", event.merchantId)
        } catch (e: Exception) {
            log.error("keycloak merchant name update failed merchantId={}", event.merchantId, e)
            throw e
        }
    }

    fun updateBranchName(event: BranchNameUpdated) {
        try {
            val merchantGroupId = findMerchantGroupId(event.merchantId)
            val group = groups().group(merchantGroupId).getSubGroups(KeycloakGroupNames.branchGroup(event.branchId), true, 0, 200, false).firstOrNull()
            if (group == null) {
                log.warn("keycloak branch group not found for update branchId={}", event.branchId)
                return
            }
            group.attributes = (group.attributes ?: emptyMap()) + mapOf(KeycloakGroupNames.ATTR_BRANCH_NAME to listOf(event.newName))
            groups().group(group.id).update(group)
            log.info("keycloak branch name updated branchId={}", event.branchId)
        } catch (e: Exception) {
            log.error("keycloak branch name update failed branchId={}", event.branchId, e)
            throw e
        }
    }

    private fun ensureRootGroup(): String {
        groups().findByName(KeycloakGroupNames.ROOT)?.let { return it.id }
        return addGroup(GroupRepresentation().apply { name = KeycloakGroupNames.ROOT })
            ?: groups().findByName(KeycloakGroupNames.ROOT)?.id
            ?: throw IllegalStateException("root merchants group not found after creation")
    }

    private fun ensureMerchantGroup(rootId: String, merchantId: UUID, merchantName: String): String =
        ensureChildGroup(rootId, KeycloakGroupNames.merchantGroup(merchantId), merchantAttributes(merchantId, merchantName))

    private fun ensureBranchGroup(merchantGroupId: String, branchId: UUID, branchName: String, merchantId: UUID): String =
        ensureChildGroup(merchantGroupId, KeycloakGroupNames.branchGroup(branchId), branchAttributes(branchId, branchName, merchantId))

    private fun ensureChildGroup(parentId: String, groupName: String, attributes: Map<String, List<String>>): String {
        val existing = groups().group(parentId).getSubGroups(groupName, true, 0, 200, false).firstOrNull()
        if (existing != null) {
            existing.attributes = attributes
            groups().group(existing.id).update(existing)
            return existing.id
        }
        val rep = GroupRepresentation().apply { name = groupName; this.attributes = attributes }
        return addSubGroup(parentId, rep)
            ?: groups().group(parentId).getSubGroups(groupName, true, 0, 200, false).firstOrNull()?.id
            ?: throw IllegalStateException("child group not found after creation: $groupName")
    }

    private fun findMerchantGroupId(merchantId: UUID): String {
        val rootId = ensureRootGroup()
        return groups().group(rootId).getSubGroups(KeycloakGroupNames.merchantGroup(merchantId), true, 0, 200, false).firstOrNull()?.id
            ?: throw NoSuchElementException("merchant group not found: $merchantId")
    }

    private fun groups(): GroupsResource = keycloak.realm(properties.realm).groups()

    // Returns the new group ID, or null on 409 Conflict so the caller can re-fetch.
    private fun addGroup(rep: GroupRepresentation): String? {
        val response = groups().add(rep)
        val location = response.getHeaderString("Location")
        val status = response.status
        response.close()
        if (status == 409) return null
        return location?.substringAfterLast("/")
            ?: throw IllegalStateException("No Location header in Keycloak group creation response")
    }

    private fun addSubGroup(parentId: String, rep: GroupRepresentation): String? {
        val response = groups().group(parentId).subGroup(rep)
        val location = response.getHeaderString("Location")
        val status = response.status
        response.close()
        if (status == 409) return null
        return location?.substringAfterLast("/")
            ?: throw IllegalStateException("No Location header in Keycloak child group creation response")
    }

    private fun GroupsResource.findByName(name: String): GroupRepresentation? =
        groups(name, true, 0, 10, false).firstOrNull()

    private fun merchantAttributes(merchantId: UUID, merchantName: String) = mapOf(
        KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
        KeycloakGroupNames.ATTR_MERCHANT_NAME to listOf(merchantName),
    )

    private fun branchAttributes(branchId: UUID, branchName: String, merchantId: UUID) = mapOf(
        KeycloakGroupNames.ATTR_BRANCH_ID to listOf(branchId.toString()),
        KeycloakGroupNames.ATTR_BRANCH_NAME to listOf(branchName),
        KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
    )
}
