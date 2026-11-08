package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.CreateFloorPlanRequest
import com.elegant.software.blitzpay.merchant.api.CreateTablesRequest
import com.elegant.software.blitzpay.merchant.api.FloorPlanImageUploadRequest
import com.elegant.software.blitzpay.merchant.api.FloorPlanImageUploadResponse
import com.elegant.software.blitzpay.merchant.api.FloorPlanResponse
import com.elegant.software.blitzpay.merchant.api.FloorResponse
import com.elegant.software.blitzpay.merchant.api.PlaceTableRequest
import com.elegant.software.blitzpay.merchant.api.SetGuestCountRequest
import com.elegant.software.blitzpay.merchant.api.TableLiveResponse
import com.elegant.software.blitzpay.merchant.api.TableResponse
import com.elegant.software.blitzpay.merchant.api.UpdateFloorAreasRequest
import com.elegant.software.blitzpay.merchant.api.UpdateFloorPlanRequest
import com.elegant.software.blitzpay.merchant.api.UpdateTableRequest
import com.elegant.software.blitzpay.merchant.service.MerchantTableService
import com.elegant.software.blitzpay.merchant.service.TableFloorBus
import com.elegant.software.blitzpay.merchant.service.TableFloorService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID


/**
 * Tables, Floor Plans and the live floor of a Branch. Setup is for a Merchant Manager or a Platform Admin;
 * the live floor is for anyone working the Branch, and a Platform Admin may read it but never clear Tables.
 */
@Tag(name = "Tables", description = "Tables, Floor Plans, Table Placements and live Table Status of a Branch")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/branches/{branchId}", version = "1")
class MerchantTableController(
    private val tableService: MerchantTableService,
    private val floorService: TableFloorService,
    private val access: TableAccess,
    private val floorBus: TableFloorBus,
) {

    @Operation(summary = "List the Branch's Tables, active and deactivated")
    @GetMapping("/tables")
    fun listTables(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): List<TableResponse> {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.listTables(branchId)
    }

    @Operation(summary = "Create Tables in bulk, one per name; each gets a permanent Table Code and Table Link")
    @PostMapping("/tables")
    fun createTables(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestBody request: CreateTablesRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<List<TableResponse>> {
        access.requireSetup(merchantId, branchId, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.createTables(branchId, request.names, request.seats))
    }

    @Operation(summary = "Rename, deactivate or reactivate a Table; its Table Code and Table Link never change")
    @PatchMapping("/tables/{tableId}")
    fun updateTable(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable tableId: UUID,
        @RequestBody request: UpdateTableRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TableResponse {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.updateTable(branchId, tableId, request)
    }

    @Operation(summary = "Place a Table on one of the Branch's Floor Plans; x and y are fractions (0..1) of the picture")
    @PutMapping("/tables/{tableId}/placement")
    fun placeTable(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable tableId: UUID,
        @RequestBody request: PlaceTableRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TableResponse {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.placeTable(branchId, tableId, request)
    }

    @Operation(summary = "Remove a Table from its Floor Plan")
    @DeleteMapping("/tables/{tableId}/placement")
    fun unplaceTable(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable tableId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TableResponse {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.unplaceTable(branchId, tableId)
    }

    @Operation(summary = "Record that an NFC tag carrying the Table Link was written for this Table (now)")
    @PutMapping("/tables/{tableId}/nfc-tag")
    fun recordNfcTag(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable tableId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TableResponse {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.recordNfcTag(branchId, tableId, Instant.now())
    }

    @Operation(summary = "Forget the Table's NFC tag, for example after it was lost")
    @DeleteMapping("/tables/{tableId}/nfc-tag")
    fun clearNfcTag(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable tableId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TableResponse {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.recordNfcTag(branchId, tableId, null)
    }

    @Operation(summary = "Delete a Table without orders; 409 when it has orders (deactivate it instead)")
    @DeleteMapping("/tables/{tableId}")
    fun deleteTable(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable tableId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<Void> {
        access.requireSetup(merchantId, branchId, jwt)
        tableService.deleteTable(branchId, tableId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List the Branch's Floor Plans")
    @GetMapping("/floor-plans")
    fun listFloorPlans(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): List<FloorPlanResponse> {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.listFloorPlans(branchId)
    }

    @Operation(summary = "Create a Floor Plan; upload its picture with the upload-url endpoint")
    @PostMapping("/floor-plans")
    fun createFloorPlan(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestBody request: CreateFloorPlanRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<FloorPlanResponse> {
        access.requireSetup(merchantId, branchId, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.createFloorPlan(branchId, request.name))
    }

    @Operation(summary = "Rename a Floor Plan")
    @PatchMapping("/floor-plans/{floorPlanId}")
    fun renameFloorPlan(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable floorPlanId: UUID,
        @RequestBody request: UpdateFloorPlanRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): FloorPlanResponse {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.renameFloorPlan(branchId, floorPlanId, request.name)
    }

    @Operation(summary = "Replace the Floor Areas traced over a Floor Plan; an empty list shows the picture again")
    @PutMapping("/floor-plans/{floorPlanId}/areas")
    fun updateFloorAreas(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable floorPlanId: UUID,
        @RequestBody request: UpdateFloorAreasRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): FloorPlanResponse {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.updateFloorAreas(branchId, floorPlanId, request.areas)
    }

    @Operation(summary = "Delete a Floor Plan; Tables placed on it become unplaced")
    @DeleteMapping("/floor-plans/{floorPlanId}")
    fun deleteFloorPlan(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable floorPlanId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<Void> {
        access.requireSetup(merchantId, branchId, jwt)
        tableService.deleteFloorPlan(branchId, floorPlanId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "Create a presigned upload URL for the Floor Plan picture",
        description = "Supported content types: image/jpeg, image/png, image/webp. PUT the picture to uploadUrl; " +
            "the Floor Plan's imageUrl then shows it.",
    )
    @PostMapping("/floor-plans/{floorPlanId}/image/upload-url")
    fun createFloorPlanImageUploadUrl(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable floorPlanId: UUID,
        @RequestBody request: FloorPlanImageUploadRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): FloorPlanImageUploadResponse {
        access.requireSetup(merchantId, branchId, jwt)
        return tableService.createFloorPlanImageUploadUrl(merchantId, branchId, floorPlanId, request.contentType)
    }

    @Operation(summary = "Live floor: Floor Plans and every active Table with its Table Status and current orders")
    @GetMapping("/floor")
    fun floor(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): FloorResponse {
        access.requireFloorRead(merchantId, branchId, jwt)
        return floorService.floor(branchId)
    }

    @Operation(summary = "Clear an Occupied Table; staff of the Branch only (403 for a Platform Admin)")
    @PostMapping("/tables/{tableId}/clear")
    fun clearTable(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable tableId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<Void> {
        access.requireFloorAction(merchantId, branchId, jwt)
        floorService.clear(branchId, tableId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "Set a Table's Guest Count; on a free Table this makes it Occupied. Staff of the Branch only " +
            "(403 for a Platform Admin)",
    )
    @PutMapping("/tables/{tableId}/guests")
    fun setGuestCount(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @PathVariable tableId: UUID,
        @RequestBody request: SetGuestCountRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TableLiveResponse {
        access.requireFloorAction(merchantId, branchId, jwt)
        return floorService.setGuestCount(branchId, tableId, request.guestCount)
    }

    @Operation(
        summary = "Stream the live floor as SSE: a `table` event (id = tableId, data = TableLiveResponse) whenever a " +
            "Table changes. Load /floor first; reload it after reconnecting",
    )
    @GetMapping("/floor/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun floorEvents(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
        response: HttpServletResponse,
    ): SseEmitter {
        access.requireFloorRead(merchantId, branchId, jwt)
        // nginx buffers responses by default, which would hold events back.
        response.setHeader("X-Accel-Buffering", "no")
        return SseEmitter(STREAM_TIMEOUT_MS).also {
            floorBus.register(branchId, it)
            // Flushes the response headers right away so clients know the stream is open before the first event.
            it.send(SseEmitter.event().comment("connected"))
        }
    }

    // Explicit content type on errors: the events endpoint only produces text/event-stream, which can't carry the body.
    private companion object {
        /** Clients reconnect after this (and reload /floor); heartbeats keep the connection alive meanwhile. */
        const val STREAM_TIMEOUT_MS = 60 * 60 * 1000L
    }
}
