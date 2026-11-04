package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.PlaceTableRequest
import com.elegant.software.blitzpay.merchant.api.TablePlacementInput
import com.elegant.software.blitzpay.merchant.api.TableUpdateInput
import com.elegant.software.blitzpay.merchant.api.UpdateTableRequest
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.iam.repository.MerchantUserAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantFloorPlan
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTable
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.service.MerchantTableService
import com.elegant.software.blitzpay.merchant.web.MerchantCallerResolver
import com.elegant.software.blitzpay.merchant.web.TableAccess
import com.elegant.software.blitzpay.merchant.web.TableAccessDeniedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant
import java.util.Base64
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableMcpToolsTest {
    private val tableService = mock<MerchantTableService>()
    private val branchRepository = mock<MerchantBranchRepository>()
    private val assignmentRepository = mock<MerchantUserAssignmentRepository>()
    private val tools = TableMcpTools(tableService, TableAccess(branchRepository, MerchantCallerResolver(assignmentRepository)))

    private val merchantId = UUID.randomUUID()
    private val branch = MerchantBranch(merchantId = merchantId, name = "Altstadt")
    private val otherBranch = MerchantBranch(merchantId = merchantId, name = "Hafen")
    private val managerId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()

    init {
        whenever(branchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(branchRepository.findById(otherBranch.id)).thenReturn(Optional.of(otherBranch))
        whenever(assignmentRepository.findAllByKeycloakUserId(managerId)).thenReturn(
            listOf(MerchantUserAssignment(keycloakUserId = managerId, merchantId = merchantId, role = MerchantOperationsRole.MERCHANT_MANAGER))
        )
        whenever(assignmentRepository.findAllByKeycloakUserId(employeeId)).thenReturn(
            listOf(MerchantUserAssignment(keycloakUserId = employeeId, merchantId = merchantId, branchId = branch.id, role = MerchantOperationsRole.MERCHANT_EMPLOYEE))
        )
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun signIn(sub: UUID, realmRoles: List<String> = emptyList()) {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$sub"}""".toByteArray())
        val jwt = Jwt.withTokenValue("h.$payload.s").header("alg", "none").subject(sub.toString())
            .claim("realm_access", mapOf("roles" to realmRoles))
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @Test
    fun `a Merchant Manager creates and lists Tables of a branch`() {
        signIn(managerId)

        tools.createTables(branch.id.toString(), listOf("1", "2"), seats = 4)
        tools.listTables(branch.id.toString())
        tools.listFloorPlans(branch.id.toString())

        verify(tableService).createTables(branch.id, listOf("1", "2"), 4)
        verify(tableService).listTables(branch.id)
        verify(tableService).listFloorPlans(branch.id)
    }

    @Test
    fun `a Platform Admin may use the tools without a Merchant assignment`() {
        signIn(UUID.randomUUID(), listOf("blitzpay-admin"))

        tools.listTables(branch.id.toString())

        verify(tableService).listTables(branch.id)
    }

    @Test
    fun `employees and anonymous callers are refused`() {
        signIn(employeeId)
        assertFailsWith<TableAccessDeniedException> { tools.createTables(branch.id.toString(), listOf("1")) }
        SecurityContextHolder.clearContext()
        assertFailsWith<TableAccessDeniedException> { tools.listTables(branch.id.toString()) }
        verify(tableService, never()).createTables(any(), any(), anyOrNull())
    }

    @Test
    fun `update and unplace resolve Tables by code`() {
        signIn(managerId)
        val table = MerchantTable(branchId = branch.id, code = "K7M2QX", name = "5")
        whenever(tableService.requireTableByCode("K7M2QX")).thenReturn(table)

        tools.updateTables(listOf(TableUpdateInput("K7M2QX", name = "Terrace 5"), TableUpdateInput("K7M2QX", seats = 6)))
        tools.unplaceTable("K7M2QX")

        verify(tableService).updateTable(branch.id, table.id, UpdateTableRequest.of(name = "Terrace 5"))
        verify(tableService).updateTable(branch.id, table.id, UpdateTableRequest.of(seats = 6))
        assertFailsWith<IllegalArgumentException> { tools.updateTables(listOf(TableUpdateInput("K7M2QX", seats = 51))) }
        assertFailsWith<IllegalArgumentException> { tools.updateTables(listOf(TableUpdateInput("K7M2QX"))) }
        verify(tableService).unplaceTable(branch.id, table.id)
    }

    @Test
    fun `place_tables places every Table of the Floor Plan's branch and refuses others before changing anything`() {
        signIn(managerId)
        val plan = MerchantFloorPlan(branchId = branch.id, name = "Inside")
        val own = MerchantTable(branchId = branch.id, code = "AAAAAA", name = "1")
        val foreign = MerchantTable(branchId = otherBranch.id, code = "BBBBBB", name = "2")
        whenever(tableService.requireFloorPlanById(plan.id)).thenReturn(plan)
        whenever(tableService.requireTableByCode("AAAAAA")).thenReturn(own)
        whenever(tableService.requireTableByCode("BBBBBB")).thenReturn(foreign)

        tools.placeTables(plan.id.toString(), listOf(TablePlacementInput("AAAAAA", 0.2, 0.8)))
        verify(tableService).placeTable(branch.id, own.id, PlaceTableRequest(plan.id, 0.2, 0.8))

        assertFailsWith<IllegalArgumentException> {
            tools.placeTables(plan.id.toString(), listOf(TablePlacementInput("AAAAAA", 0.5, 0.5), TablePlacementInput("BBBBBB", 0.5, 0.5)))
        }
        assertFailsWith<IllegalArgumentException> {
            tools.placeTables(plan.id.toString(), listOf(TablePlacementInput("AAAAAA", 1.5, 0.5)))
        }
        verify(tableService, never()).placeTable(branch.id, own.id, PlaceTableRequest(plan.id, 0.5, 0.5))
    }

    @Test
    fun `tool annotations describe read-only, non-destructive and destructive tools accurately`() {
        val byName = TableMcpTools::class.java.methods.mapNotNull { it.getAnnotation(McpTool::class.java) }.associateBy { it.name }

        assertEquals(setOf("list_floor_plans", "list_tables", "create_tables", "update_tables", "place_tables", "unplace_table"), byName.keys)
        listOf("list_floor_plans", "list_tables").forEach { assertTrue(byName.getValue(it).annotations.readOnlyHint, it) }
        listOf("create_tables", "update_tables", "place_tables").forEach {
            assertFalse(byName.getValue(it).annotations.readOnlyHint, it)
            assertFalse(byName.getValue(it).annotations.destructiveHint, it)
        }
        assertFalse(byName.getValue("create_tables").annotations.idempotentHint)
        assertTrue(byName.getValue("unplace_table").annotations.destructiveHint)
        byName.values.forEach { assertFalse(it.annotations.openWorldHint, it.name) }
    }
}
