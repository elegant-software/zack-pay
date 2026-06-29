package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.repository.MerchantUserAssignmentRepository
import com.elegant.software.blitzpay.merchant.iam.web.CreateAssignmentRequest
import com.elegant.software.blitzpay.merchant.iam.web.MerchantUserAssignmentResponse
import com.elegant.software.blitzpay.merchant.iam.web.UserGroupMembership
import com.elegant.software.blitzpay.merchant.iam.web.UserSearchResult
import com.elegant.software.blitzpay.merchant.api.MerchantLookupGateway
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.GroupsResource
import org.keycloak.representations.idm.GroupRepresentation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Profile("!contract-test")
class UserManagementService(
    private val keycloak: Keycloak,
    private val keycloakProperties: KeycloakProperties,
    private val assignmentRepository: MerchantUserAssignmentRepository,
    private val merchantLookup: MerchantLookupGateway,
) {
    private val log = LoggerFactory.getLogger(UserManagementService::class.java)

    fun searchUsers(query: String): List<UserSearchResult> {
        val usersResource = keycloak.realm(keycloakProperties.realm).users()

        val candidates = usersResource.search(query.ifBlank { null }, 0, 50)

        return candidates.take(50).map { u ->
            UserSearchResult(
                id = u.id,
                displayName = listOfNotNull(u.firstName, u.lastName).joinToString(" ").ifBlank { u.username ?: u.id },
                email = u.email,
                enabled = u.isEnabled,
                groups = usersResource.get(u.id).groups().mapNotNull(::toUserGroupMembership),
            )
        }
    }

    fun getAssignments(userId: UUID): List<MerchantUserAssignmentResponse> =
        assignmentRepository.findAllByKeycloakUserId(userId).map { it.toResponse() }

    @Transactional
    fun createAssignment(userId: UUID, request: CreateAssignmentRequest): MerchantUserAssignmentResponse {
        val merchant = merchantLookup.requireMerchant(request.merchantId)

        if (request.branchId != null) {
            val branch = merchantLookup.findBranch(request.branchId)
                ?: throw IllegalArgumentException("branch not found: ${request.branchId}")
            require(branch.merchantId == request.merchantId) {
                "branch ${request.branchId} does not belong to merchant ${request.merchantId}"
            }
        }

        require(request.role != MerchantOperationsRole.MERCHANT_EMPLOYEE || request.branchId != null) {
            "MERCHANT_EMPLOYEE assignment requires a branchId"
        }

        if (assignmentRepository.existsByKeycloakUserIdAndMerchantIdAndBranchId(
                userId, request.merchantId, request.branchId
            )
        ) {
            throw IllegalStateException("assignment already exists for user $userId on merchant ${request.merchantId} branch ${request.branchId}")
        }

        val groupId = resolveTargetGroupId(request.merchantId, request.branchId)
        val userResource = keycloak.realm(keycloakProperties.realm).users().get(userId.toString())
        userResource.joinGroup(groupId)
        log.info("keycloak group assigned userId={} groupId={}", userId, groupId)

        resolveMerchantAppUsersGroupId()?.let { merchantAppGroupId ->
            userResource.joinGroup(merchantAppGroupId)
            log.info("merchant-app-users group assigned userId={}", userId)
        }

        resolveRoleGroupId(request.role)?.let { roleGroupId ->
            userResource.joinGroup(roleGroupId)
            log.info("role group assigned userId={} role={} groupId={}", userId, request.role, roleGroupId)
        }

        val assignment = assignmentRepository.save(
            MerchantUserAssignment(
                keycloakUserId = userId,
                merchantId = request.merchantId,
                branchId = request.branchId,
                role = request.role,
                employeeId = request.employeeId,
            )
        )

        return assignment.toResponse(
            merchantName = merchant.name,
            branchName = request.branchId?.let { merchantLookup.findBranch(it)?.name },
        )
    }

    @Transactional
    fun revokeAssignment(userId: UUID, assignmentId: UUID) {
        val assignment = assignmentRepository.findById(assignmentId).orElseThrow {
            NoSuchElementException("assignment not found: $assignmentId")
        }
        require(assignment.keycloakUserId == userId) {
            "assignment $assignmentId does not belong to user $userId"
        }

        val groupId = resolveTargetGroupId(assignment.merchantId, assignment.branchId)
        val userResource = keycloak.realm(keycloakProperties.realm).users().get(userId.toString())
        userResource.leaveGroup(groupId)
        log.info("keycloak group revoked userId={} groupId={}", userId, groupId)

        assignmentRepository.delete(assignment)

        val remaining = assignmentRepository.findAllByKeycloakUserId(userId)

        if (remaining.none { it.role == assignment.role }) {
            resolveRoleGroupId(assignment.role)?.let { roleGroupId ->
                userResource.leaveGroup(roleGroupId)
                log.info("role group revoked userId={} role={} groupId={}", userId, assignment.role, roleGroupId)
            }
        }

        if (remaining.isEmpty()) {
            resolveMerchantAppUsersGroupId()?.let { merchantAppGroupId ->
                userResource.leaveGroup(merchantAppGroupId)
                log.info("merchant-app-users group revoked userId={}", userId)
            }
        }
    }

    private fun resolveMerchantAppUsersGroupId(): String? =
        groups().findByName(KeycloakGroupNames.MERCHANT_APP_USERS)?.id.also {
            if (it == null) log.warn("'{}' group not found in Keycloak", KeycloakGroupNames.MERCHANT_APP_USERS)
        }

    private fun resolveRoleGroupId(role: MerchantOperationsRole): String? {
        val merchantAppGroupId = resolveMerchantAppUsersGroupId() ?: return null
        val roleName = when (role) {
            MerchantOperationsRole.MERCHANT_MANAGER -> KeycloakGroupNames.MERCHANT_MANAGER
            MerchantOperationsRole.MERCHANT_EMPLOYEE -> KeycloakGroupNames.MERCHANT_EMPLOYEE
        }
        return groups().group(merchantAppGroupId).getSubGroups(roleName, true, 0, 200, false).firstOrNull()?.id.also {
            if (it == null) log.warn("role group '{}' not found under merchant-app-users", roleName)
        }
    }

    private fun resolveTargetGroupId(merchantId: UUID, branchId: UUID?): String {
        val rootId = groups().findByName(KeycloakGroupNames.ROOT)?.id
            ?: throw IllegalStateException(
                "Keycloak root group '${KeycloakGroupNames.ROOT}' does not exist. " +
                    "Activate a merchant to trigger automatic group creation, or create the group manually in Keycloak."
            )

        val merchantGroupName = KeycloakGroupNames.merchantGroup(merchantId)
        val merchantGroupId = groups().group(rootId).getSubGroups(merchantGroupName, true, 0, 200, false).firstOrNull()?.id
            ?: throw IllegalStateException(
                "Keycloak group '$merchantGroupName' not found under '${KeycloakGroupNames.ROOT}'. " +
                    "This group is created automatically when a MerchantActivated event is published. " +
                    "Ensure the merchant $merchantId has been activated and the event was processed successfully."
            )

        if (branchId == null) return merchantGroupId

        val branchGroupName = KeycloakGroupNames.branchGroup(branchId)
        return groups().group(merchantGroupId).getSubGroups(branchGroupName, true, 0, 200, false).firstOrNull()?.id
            ?: throw IllegalStateException(
                "Keycloak group '$branchGroupName' not found under '$merchantGroupName'. " +
                    "This group is created automatically when a BranchCreated event is published. " +
                    "Ensure the branch $branchId has been created and the event was processed successfully."
            )
    }

    private fun groups(): GroupsResource = keycloak.realm(keycloakProperties.realm).groups()

    private fun GroupsResource.findByName(name: String): GroupRepresentation? =
        groups(name, true, 0, 10, false).firstOrNull()

    private fun MerchantUserAssignment.toResponse(
        merchantName: String? = null,
        branchName: String? = null,
    ): MerchantUserAssignmentResponse {
        val resolvedMerchantName = merchantName
            ?: merchantLookup.findMerchant(merchantId)?.name
            ?: merchantId.toString()
        val resolvedBranchName = branchName ?: branchId?.let { merchantLookup.findBranch(it)?.name }
        return MerchantUserAssignmentResponse(
            id = id,
            merchantId = merchantId,
            merchantName = resolvedMerchantName,
            branchId = branchId,
            branchName = resolvedBranchName,
            role = role,
            employeeId = employeeId,
        )
    }

    private fun toUserGroupMembership(group: GroupRepresentation): UserGroupMembership? {
        val merchantId = group.attributeUuid(KeycloakGroupNames.ATTR_MERCHANT_ID)
            ?: group.groupUuid(KeycloakGroupNames.MERCHANT_PREFIX)
        val branchId = group.attributeUuid(KeycloakGroupNames.ATTR_BRANCH_ID)
            ?: group.groupUuid(KeycloakGroupNames.BRANCH_PREFIX)

        if (merchantId == null && branchId == null) return null

        val branch = branchId?.let { merchantLookup.findBranch(it) }
        val resolvedMerchantId = merchantId ?: branch?.merchantId
        val merchantName = group.firstAttribute(KeycloakGroupNames.ATTR_MERCHANT_NAME)
            ?: resolvedMerchantId?.let { merchantLookup.findMerchant(it)?.name }
            ?: return null
        val branchName = group.firstAttribute(KeycloakGroupNames.ATTR_BRANCH_NAME) ?: branch?.name

        return UserGroupMembership(
            id = group.id,
            merchantName = merchantName,
            branchName = branchName,
        )
    }

    private fun GroupRepresentation.firstAttribute(key: String): String? =
        attributes?.get(key)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun GroupRepresentation.attributeUuid(key: String): UUID? =
        firstAttribute(key)?.toUuidOrNull()

    private fun GroupRepresentation.groupUuid(prefix: String): UUID? =
        name?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toUuidOrNull()

    private fun String.toUuidOrNull(): UUID? =
        runCatching { UUID.fromString(this) }.getOrNull()
}
