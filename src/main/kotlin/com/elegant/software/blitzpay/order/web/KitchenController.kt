package com.elegant.software.blitzpay.order.web

import com.elegant.software.blitzpay.order.api.KitchenOrder
import com.elegant.software.blitzpay.order.api.UpdatePreparationStatusRequest
import com.elegant.software.blitzpay.order.service.KitchenOrderBus
import com.elegant.software.blitzpay.order.service.KitchenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

data class KitchenBranding(val logoUrl: String?)

@Tag(name = "Kitchen Screen", description = "Live Orders of one Branch and their Preparation Status")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchant/branches/{branchId}/kitchen", version = "1")
class KitchenController(
    private val kitchenService: KitchenService,
    private val bus: KitchenOrderBus,
) {
    private val log = LoggerFactory.getLogger(KitchenController::class.java)

    @Operation(summary = "List today's open Orders of the Branch (not READY, not cancelled), oldest first; used on (re)connect")
    @GetMapping("/orders")
    fun listOpenOrders(
        @PathVariable branchId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<List<KitchenOrder>> {
        BranchGroupAccess.require(jwt, branchId)
        return ResponseEntity.ok(kitchenService.listOpenOrders(branchId))
    }

    @Operation(summary = "Stream the Branch's Order events (order.created, order.changed, order.cancelled) as SSE")
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable branchId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): SseEmitter {
        BranchGroupAccess.require(jwt, branchId)
        log.info("kitchen SSE client connected branchId={}", branchId)
        return SseEmitter(STREAM_TIMEOUT_MS).also {
            bus.register(branchId, it)
            // Flushes the response headers right away so clients know the stream is open before the first event.
            it.send(SseEmitter.event().comment("connected"))
        }
    }

    @Operation(summary = "Advance an Order's Preparation Status: RECEIVED → PREPARING → READY")
    @PostMapping("/orders/{orderId}/preparation-status")
    fun updatePreparationStatus(
        @PathVariable branchId: UUID,
        @PathVariable orderId: UUID,
        @RequestBody request: UpdatePreparationStatusRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KitchenOrder> {
        BranchGroupAccess.require(jwt, branchId)
        return ResponseEntity.ok(kitchenService.updatePreparationStatus(branchId, orderId, request.status))
    }

    @Operation(summary = "Branding of the Branch's Merchant for the Kitchen Screen header; logoUrl is a short-lived URL or null")
    @GetMapping("/branding")
    fun branding(@PathVariable branchId: UUID, @AuthenticationPrincipal jwt: Jwt?): KitchenBranding {
        BranchGroupAccess.require(jwt, branchId)
        return KitchenBranding(kitchenService.logoUrl(branchId))
    }

    @Operation(summary = "List stations available to this authorized branch, including unassigned work")
    @GetMapping("/stations")
    fun stations(@PathVariable branchId: UUID, @AuthenticationPrincipal jwt: Jwt?): List<com.elegant.software.blitzpay.merchant.api.PreparationStation> {
        BranchGroupAccess.require(jwt, branchId)
        return kitchenService.stations(branchId)
    }

    @GetMapping("/stations/{stationId}/orders")
    fun stationOrders(@PathVariable branchId: UUID, @PathVariable stationId: UUID,
                      @AuthenticationPrincipal jwt: Jwt?): List<KitchenOrder> {
        BranchGroupAccess.require(jwt, branchId)
        return kitchenService.listStationOrders(branchId, stationId)
    }

    @GetMapping("/stations/{stationId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stationStream(@PathVariable branchId: UUID, @PathVariable stationId: UUID,
                      @AuthenticationPrincipal jwt: Jwt?): SseEmitter {
        BranchGroupAccess.require(jwt, branchId)
        kitchenService.requireStation(branchId, stationId)
        return SseEmitter(STREAM_TIMEOUT_MS).also {
            bus.register(branchId, it, stationId)
            it.send(SseEmitter.event().comment("connected"))
        }
    }

    @PostMapping("/stations/{stationId}/orders/{orderId}/preparation-status")
    fun stationStatus(@PathVariable branchId: UUID, @PathVariable stationId: UUID,
                      @PathVariable orderId: UUID, @RequestBody request: UpdatePreparationStatusRequest,
                      @AuthenticationPrincipal jwt: Jwt?): KitchenOrder {
        BranchGroupAccess.require(jwt, branchId)
        return kitchenService.updateStationPreparationStatus(branchId, orderId, stationId, request.status)
    }

    private companion object {
        /** Screens reconnect (and re-sync via the list endpoint) after this; heartbeats keep the connection alive meanwhile. */
        const val STREAM_TIMEOUT_MS = 60 * 60 * 1000L
    }
}
