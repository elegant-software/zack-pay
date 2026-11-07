package com.elegant.software.blitzpay.order.web

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.api.AddOrderItemRequest
import com.elegant.software.blitzpay.order.api.CreateOrderRequest
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.OrderSummaryResponse
import com.elegant.software.blitzpay.order.api.UpdateOrderItemRequest
import com.elegant.software.blitzpay.order.service.OrderService
import com.elegant.software.blitzpay.order.service.UnauthenticatedException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.Base64
import java.util.UUID

@Tag(name = "Orders", description = "Create and inspect orders for shoppers")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/orders", version = "1")
class ShopperOrderController(private val orderService: OrderService) {
    private val log = LoggerFactory.getLogger(ShopperOrderController::class.java)

    @Operation(summary = "Create an order and initiate payment via the chosen provider")
    @PostMapping
    fun create(
        @RequestBody request: CreateOrderRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val shopperId = requireSubject(authorization)
        log.info("order create request shopperId={} itemCount={} productIds={}", shopperId, request.items.size, request.items.map { it.productId })
        val response = orderService.createShopperOrder(request, shopperId)
        LogContext.with(LogContext.ORDER_ID to response.orderId) {
            log.info("order created orderId={} totalAmountMinor={} currency={} itemCount={}", response.orderId, response.totalAmountMinor, response.currency, response.items.size)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "List recent orders for the authenticated shopper (last 7 days)")
    @GetMapping
    fun list(@RequestHeader(name = "Authorization", required = false) authorization: String?): ResponseEntity<List<OrderSummaryResponse>> {
        val shopperId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.listShopperOrders(shopperId))
    }

    @Operation(summary = "Get the latest business status for an order")
    @GetMapping("/{orderId}")
    fun get(@PathVariable orderId: String): ResponseEntity<OrderResponse> =
        ResponseEntity.ok(orderService.get(orderId))

    @Operation(summary = "Add an item to an unpaid order")
    @PostMapping("/{orderId}/items")
    fun addItem(
        @PathVariable orderId: String,
        @RequestBody request: AddOrderItemRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val shopperId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.addItem(orderId, request, shopperId))
    }

    @Operation(summary = "Update quantity of an item in an unpaid order")
    @PatchMapping("/{orderId}/items/{itemId}")
    fun updateItem(
        @PathVariable orderId: String,
        @PathVariable itemId: UUID,
        @RequestBody request: UpdateOrderItemRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val shopperId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.updateItemQuantity(orderId, itemId, request, shopperId))
    }

    @Operation(summary = "Remove an item from an unpaid order (auto-cancels if last item)")
    @DeleteMapping("/{orderId}/items/{itemId}")
    fun removeItem(
        @PathVariable orderId: String,
        @PathVariable itemId: UUID,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val shopperId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.removeItem(orderId, itemId, shopperId))
    }

    @Operation(summary = "Cancel an unpaid order")
    @PostMapping("/{orderId}/cancel")
    fun cancel(
        @PathVariable orderId: String,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val shopperId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.cancelOrder(orderId, shopperId))
    }

    private fun requireSubject(authorization: String?): String =
        extractSubject(authorization) ?: throw UnauthenticatedException()

    private fun extractSubject(authorization: String?): String? {
        val token = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.mapCatching { payload ->
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(512)
    }
}
