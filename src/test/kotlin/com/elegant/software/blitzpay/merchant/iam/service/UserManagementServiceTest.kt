package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.repository.MerchantUserAssignmentRepository
import com.elegant.software.blitzpay.merchant.iam.web.CreateAssignmentRequest
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.GroupResource
import org.keycloak.admin.client.resource.GroupsResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class UserManagementServiceTest {

    private val keycloak = mock<Keycloak>()
    private val realmResource = mock<RealmResource>()
    private val usersResource = mock<UsersResource>()
    private val userResource = mock<UserResource>()
    private val groupsResource = mock<GroupsResource>()
    private val assignmentRepository = mock<MerchantUserAssignmentRepository>()
    private val merchantRepository = mock<MerchantApplicationRepository>()
    private val branchRepository = mock<MerchantBranchRepository>()
    private val properties = KeycloakProperties(
        serverUrl = "http://keycloak", realm = "test", adminClientId = "admin", adminClientSecret = "secret"
    )

    private val service = UserManagementService(
        keycloak, properties, assignmentRepository, merchantRepository, branchRepository
    )

    private val userId = UUID.randomUUID()
    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val assignmentId = UUID.randomUUID()
    private val rootGroupId = "root-id"
    private val merchantGroupId = "merchant-group-id"
    private val branchGroupId = "branch-group-id"

    @BeforeEach
    fun setUp() {
        whenever(keycloak.realm(properties.realm)).thenReturn(realmResource)
        whenever(realmResource.users()).thenReturn(usersResource)
        whenever(realmResource.groups()).thenReturn(groupsResource)
    }

    private fun group(id: String, name: String, attributes: Map<String, List<String>> = emptyMap()) =
        GroupRepresentation().apply { this.id = id; this.name = name; this.attributes = attributes }

    private fun groupResource(parentId: String): GroupResource =
        mock<GroupResource>().also { whenever(groupsResource.group(parentId)).thenReturn(it) }

    private fun stubRootGroup() {
        whenever(groupsResource.groups(KeycloakGroupNames.ROOT, true, 0, 10, false))
            .thenReturn(listOf(group(rootGroupId, KeycloakGroupNames.ROOT)))
    }

    private fun stubMerchantGroup(merchantId: UUID) {
        val rootResource = groupResource(rootGroupId)
        whenever(rootResource.getSubGroups(KeycloakGroupNames.merchantGroup(merchantId), true, 0, 200, false))
            .thenReturn(listOf(group(merchantGroupId, KeycloakGroupNames.merchantGroup(merchantId))))
    }

    private fun stubBranchGroup(merchantId: UUID, branchId: UUID) {
        val merchantResource = groupResource(merchantGroupId)
        whenever(merchantResource.getSubGroups(KeycloakGroupNames.branchGroup(branchId), true, 0, 200, false))
            .thenReturn(listOf(group(branchGroupId, KeycloakGroupNames.branchGroup(branchId))))
    }

    @Test
    fun `searchUsers delegates to Keycloak and maps results`() {
        val kcUser = UserRepresentation().apply {
            id = "kc-id"; firstName = "Alice"; lastName = "Smith"; email = "alice@example.com"; isEnabled = true
        }
        whenever(usersResource.search("alice", 0, 50)).thenReturn(listOf(kcUser))
        whenever(usersResource.get("kc-id")).thenReturn(userResource)
        whenever(userResource.groups()).thenReturn(listOf(
            GroupRepresentation().apply {
                id = "ignored-group-id"; name = "blitzpay-mobile-users"; path = "/blitzpay-mobile-users"
            },
            GroupRepresentation().apply {
                id = "merchant-group-id"
                name = KeycloakGroupNames.merchantGroup(merchantId)
                path = "/merchants/${KeycloakGroupNames.merchantGroup(merchantId)}"
                attributes = mapOf(
                    KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
                    KeycloakGroupNames.ATTR_MERCHANT_NAME to listOf("Test Merchant"),
                )
            },
            GroupRepresentation().apply {
                id = "branch-group-id"
                name = KeycloakGroupNames.branchGroup(branchId)
                path = "/merchants/${KeycloakGroupNames.merchantGroup(merchantId)}/${KeycloakGroupNames.branchGroup(branchId)}"
                attributes = mapOf(
                    KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
                    KeycloakGroupNames.ATTR_MERCHANT_NAME to listOf("Test Merchant"),
                    KeycloakGroupNames.ATTR_BRANCH_ID to listOf(branchId.toString()),
                    KeycloakGroupNames.ATTR_BRANCH_NAME to listOf("Branch A"),
                )
            }
        ))

        val results = service.searchUsers("alice")

        assertEquals(1, results.size)
        assertEquals("kc-id", results[0].id)
        assertEquals("Alice Smith", results[0].displayName)
        assertEquals("alice@example.com", results[0].email)
        assertEquals(2, results[0].groups.size)
        assertEquals("Test Merchant", results[0].groups[0].merchantName)
        assertEquals(null, results[0].groups[0].branchName)
        assertEquals("Test Merchant", results[0].groups[1].merchantName)
        assertEquals("Branch A", results[0].groups[1].branchName)
    }

    @Test
    fun `createAssignment for MERCHANT_MANAGER places user in merchant group and persists record`() {
        stubRootGroup()
        stubMerchantGroup(merchantId)
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchantWithId(merchantId, "Test Merchant")))
        whenever(assignmentRepository.existsByKeycloakUserIdAndMerchantApplicationIdAndBranchId(userId, merchantId, null)).thenReturn(false)
        whenever(assignmentRepository.save(any<MerchantUserAssignment>())).thenAnswer { it.arguments[0] as MerchantUserAssignment }
        whenever(usersResource.get(userId.toString())).thenReturn(userResource)

        val request = CreateAssignmentRequest(merchantId = merchantId, branchId = null, role = MerchantOperationsRole.MERCHANT_MANAGER, employeeId = null)
        val response = service.createAssignment(userId, request)

        verify(userResource).joinGroup(merchantGroupId)
        assertEquals(merchantId, response.merchantId)
        assertEquals(MerchantOperationsRole.MERCHANT_MANAGER, response.role)
    }

    @Test
    fun `createAssignment for MERCHANT_EMPLOYEE places user in branch group`() {
        stubRootGroup()
        stubMerchantGroup(merchantId)
        stubBranchGroup(merchantId, branchId)
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchantWithId(merchantId, "Test Merchant")))
        whenever(branchRepository.findById(branchId)).thenReturn(Optional.of(branchWithId(branchId, merchantId, "Branch A")))
        whenever(assignmentRepository.existsByKeycloakUserIdAndMerchantApplicationIdAndBranchId(userId, merchantId, branchId)).thenReturn(false)
        whenever(assignmentRepository.save(any<MerchantUserAssignment>())).thenAnswer { it.arguments[0] as MerchantUserAssignment }
        whenever(usersResource.get(userId.toString())).thenReturn(userResource)

        val request = CreateAssignmentRequest(merchantId = merchantId, branchId = branchId, role = MerchantOperationsRole.MERCHANT_EMPLOYEE, employeeId = null)
        val response = service.createAssignment(userId, request)

        verify(userResource).joinGroup(branchGroupId)
        assertEquals(branchId, response.branchId)
    }

    @Test
    fun `createAssignment rejects MERCHANT_EMPLOYEE without branchId`() {
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchantWithId(merchantId, "M")))
        whenever(assignmentRepository.existsByKeycloakUserIdAndMerchantApplicationIdAndBranchId(any(), any(), any())).thenReturn(false)

        val request = CreateAssignmentRequest(merchantId = merchantId, branchId = null, role = MerchantOperationsRole.MERCHANT_EMPLOYEE, employeeId = null)

        assertThrows<IllegalArgumentException> { service.createAssignment(userId, request) }
        verify(userResource, never()).joinGroup(any())
    }

    @Test
    fun `createAssignment rejects duplicate assignment`() {
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchantWithId(merchantId, "M")))
        whenever(assignmentRepository.existsByKeycloakUserIdAndMerchantApplicationIdAndBranchId(userId, merchantId, null)).thenReturn(true)

        val request = CreateAssignmentRequest(merchantId = merchantId, branchId = null, role = MerchantOperationsRole.MERCHANT_MANAGER, employeeId = null)

        assertThrows<IllegalStateException> { service.createAssignment(userId, request) }
        verify(userResource, never()).joinGroup(any())
    }

    @Test
    fun `revokeAssignment removes Keycloak membership and deletes record`() {
        val assignment = MerchantUserAssignment(
            id = assignmentId, keycloakUserId = userId, merchantApplicationId = merchantId,
            branchId = null, role = MerchantOperationsRole.MERCHANT_MANAGER
        )
        stubRootGroup()
        stubMerchantGroup(merchantId)
        whenever(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment))
        whenever(usersResource.get(userId.toString())).thenReturn(userResource)
        whenever(assignmentRepository.findAllByKeycloakUserId(userId)).thenReturn(emptyList())

        service.revokeAssignment(userId, assignmentId)

        verify(userResource).leaveGroup(merchantGroupId)
        verify(assignmentRepository).delete(assignment)
    }

    @Test
    fun `revokeAssignment rejects if assignment belongs to different user`() {
        val otherUserId = UUID.randomUUID()
        val assignment = MerchantUserAssignment(
            id = assignmentId, keycloakUserId = otherUserId, merchantApplicationId = merchantId,
            branchId = null, role = MerchantOperationsRole.MERCHANT_MANAGER
        )
        whenever(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment))

        assertThrows<IllegalArgumentException> { service.revokeAssignment(userId, assignmentId) }
        verify(userResource, never()).leaveGroup(any())
    }

    private fun merchantWithId(id: UUID, name: String): MerchantApplication =
        MerchantApplication(
            id = id,
            applicationReference = "REF-$id",
            merchantName = name,
            businessProfile = BusinessProfile(
                legalBusinessName = name, businessType = "RETAIL",
                registrationNumber = "REG-$id", operatingCountry = "DE",
                primaryBusinessAddress = "Test Street"
            ),
            primaryContact = PrimaryContact(fullName = "Test", email = "test@test.com", phoneNumber = "0000"),
        )

    private fun branchWithId(id: UUID, merchantApplicationId: UUID, name: String): MerchantBranch =
        MerchantBranch(id = id, merchantApplicationId = merchantApplicationId, name = name)
}
