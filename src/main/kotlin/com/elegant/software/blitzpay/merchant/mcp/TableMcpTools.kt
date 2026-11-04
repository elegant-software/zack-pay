package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.FloorPlanResponse
import com.elegant.software.blitzpay.merchant.api.PlaceTableRequest
import com.elegant.software.blitzpay.merchant.api.TablePlacementInput
import com.elegant.software.blitzpay.merchant.api.TableUpdateInput
import com.elegant.software.blitzpay.merchant.api.TableResponse
import com.elegant.software.blitzpay.merchant.api.UpdateTableRequest
import com.elegant.software.blitzpay.merchant.service.MerchantTableService
import com.elegant.software.blitzpay.merchant.web.TableAccess
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Lets a merchant's AI assistant set up Tables: look at a Floor Plan picture, create and number Tables and place them.
 * Same rules as the REST API: Merchant Manager or Platform Admin only. No deletes (deactivate in the UI instead) and
 * no live-service actions such as clearing Tables or taking payments.
 */
@Suppress("unused")
@Component
class TableMcpTools(
    private val tableService: MerchantTableService,
    private val access: TableAccess,
) {

    @McpTool(
        name = "list_floor_plans",
        description = "List a branch's Floor Plans (named pictures of a room or area such as \"Inside\" or \"Terrace\"). " +
            "imageUrl is a short-lived link to the picture (null when none was uploaded): fetch it to look at the room, " +
            "count the tables and work out where each one is before calling place_tables.",
        annotations = McpTool.McpAnnotations(title = "Floor Plans: List", readOnlyHint = true, openWorldHint = false)
    )
    fun listFloorPlans(branchId: String): List<FloorPlanResponse> {
        val branch = access.requireSetup(UUID.fromString(branchId), currentJwt())
        return tableService.listFloorPlans(branch.id)
    }

    @McpTool(
        name = "list_tables",
        description = "List a branch's Tables with their permanent tableCode, editable name, active flag, Table Link and " +
            "placement (floorPlanId plus x,y as fractions 0..1 of the Floor Plan picture; null when not placed).",
        annotations = McpTool.McpAnnotations(title = "Tables: List", readOnlyHint = true, openWorldHint = false)
    )
    fun listTables(branchId: String): List<TableResponse> {
        val branch = access.requireSetup(UUID.fromString(branchId), currentJwt())
        return tableService.listTables(branch.id)
    }

    @McpTool(
        name = "create_tables",
        description = "Create Tables in a branch, one per name (for example [\"1\", \"2\", \"Terrace 1\"]); max " +
            "${MerchantTableService.MAX_TABLES_PER_REQUEST} per call. Each new Table gets a permanent tableCode and Table Link " +
            "for its QR code / NFC tag. Optional seats (1-50, the guests a Table is set up for) applies to every Table " +
            "created; call once per seat count when Tables differ. Not idempotent: calling twice creates duplicates, so " +
            "check list_tables first.",
        annotations = McpTool.McpAnnotations(title = "Tables: Create", destructiveHint = false, idempotentHint = false, openWorldHint = false)
    )
    fun createTables(branchId: String, names: List<String>, seats: Int? = null): List<TableResponse> {
        val branch = access.requireSetup(UUID.fromString(branchId), currentJwt())
        return tableService.createTables(branch.id, names, seats)
    }

    @McpTool(
        name = "update_tables",
        description = "Update Tables by tableCode: a new name and/or seats (1-50, the guests the Table is set up for); " +
            "omitted fields stay unchanged. Renaming never changes a Table's code or link, so printed QR codes and NFC " +
            "tags keep working.",
        annotations = McpTool.McpAnnotations(title = "Tables: Update", destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    fun updateTables(updates: List<TableUpdateInput>): List<TableResponse> {
        require(updates.size <= MerchantTableService.MAX_TABLES_PER_REQUEST) {
            "At most ${MerchantTableService.MAX_TABLES_PER_REQUEST} Tables per call"
        }
        val jwt = currentJwt()
        val tables = updates.map { input ->
            require(input.name != null || input.seats != null) { "Give a name or seats for Table ${input.tableCode}" }
            input.seats?.let {
                require(it in 1..MerchantTableService.MAX_SEATS) { "seats must be between 1 and 50 for Table ${input.tableCode}" }
            }
            val table = tableService.requireTableByCode(input.tableCode)
            access.requireSetup(table.branchId, jwt)
            table to input
        }
        return tables.map { (table, input) ->
            tableService.updateTable(table.branchId, table.id, UpdateTableRequest.of(name = input.name, seats = input.seats))
        }
    }

    @McpTool(
        name = "place_tables",
        description = "Place Tables on a Floor Plan by tableCode. x and y are fractions 0..1 of the picture's width and " +
            "height, measured from its top-left corner. Every Table must belong to the Floor Plan's branch. " +
            "Placing an already placed Table moves it.",
        annotations = McpTool.McpAnnotations(title = "Tables: Place on Floor Plan", destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    fun placeTables(floorPlanId: String, placements: List<TablePlacementInput>): List<TableResponse> {
        require(placements.size <= MerchantTableService.MAX_TABLES_PER_REQUEST) {
            "At most ${MerchantTableService.MAX_TABLES_PER_REQUEST} Tables per call"
        }
        val floorPlan = tableService.requireFloorPlanById(UUID.fromString(floorPlanId))
        access.requireSetup(floorPlan.branchId, currentJwt())
        val tables = placements.map { input ->
            val table = tableService.requireTableByCode(input.tableCode)
            require(table.branchId == floorPlan.branchId) {
                "Table ${input.tableCode} does not belong to the Floor Plan's branch"
            }
            require(input.x in 0.0..1.0 && input.y in 0.0..1.0) { "x and y must be between 0 and 1 for Table ${input.tableCode}" }
            table to input
        }
        return tables.map { (table, input) ->
            tableService.placeTable(floorPlan.branchId, table.id, PlaceTableRequest(floorPlan.id, input.x, input.y))
        }
    }

    @McpTool(
        name = "unplace_table",
        description = "Remove a Table (by tableCode) from its Floor Plan. The Table itself stays and can be placed again.",
        annotations = McpTool.McpAnnotations(title = "Table: Unplace", destructiveHint = true, idempotentHint = true, openWorldHint = false)
    )
    fun unplaceTable(tableCode: String): TableResponse {
        val table = tableService.requireTableByCode(tableCode)
        access.requireSetup(table.branchId, currentJwt())
        return tableService.unplaceTable(table.branchId, table.id)
    }

    /** MCP tool calls run on the request thread in servlet apps, so the caller's verified token is available here. */
    private fun currentJwt(): Jwt? = SecurityContextHolder.getContext().authentication?.principal as? Jwt
}
