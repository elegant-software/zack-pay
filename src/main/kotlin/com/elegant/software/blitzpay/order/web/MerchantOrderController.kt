package com.elegant.software.blitzpay.order.web

import com.elegant.software.blitzpay.order.api.AddOrderItemRequest
import com.elegant.software.blitzpay.order.api.BranchCustomerResponse
import com.elegant.software.blitzpay.order.api.CreateMerchantOrderRequest
import com.elegant.software.blitzpay.order.api.MerchantItemPriceOverrideRequest
import com.elegant.software.blitzpay.order.api.MerchantOrderResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.SettleOrderRequest
import com.elegant.software.blitzpay.order.api.UpdateOrderItemRequest
import com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway
import com.elegant.software.blitzpay.shopper.api.ShopperSearchResult
import com.elegant.software.blitzpay.order.service.OrderService
import com.elegant.software.blitzpay.order.service.UnauthenticatedException
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

@Tag(name = "Merchant Orders", description = "Create and list orders for authenticated merchant branches")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchant/orders", version = "1")
class MerchantOrderController(
    private val orderService: OrderService,
    private val shopperProfileGateway: ShopperProfileGateway,
) {

    @Operation(summary = "Create an order from branch products and return a QR code for customer payment")
    @PostMapping
    fun create(
        @RequestBody request: CreateMerchantOrderRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<MerchantOrderResponse> {
        val merchantUserId = requireSubject(authorization)
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createMerchantOrder(request, merchantUserId))
    }

    @Operation(summary = "List today's orders for the given branch, with optional status filter")
    @GetMapping
    fun list(
        @RequestParam branchId: UUID,
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false, defaultValue = "UTC") timezone: String,
    ): ResponseEntity<List<OrderResponse>> {
        val zoneId = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
        return ResponseEntity.ok(orderService.listMerchantOrders(branchId, status, zoneId))
    }

    @Operation(summary = "Add an item to an unpaid merchant order")
    @PostMapping("/{orderId}/items")
    fun addItem(
        @PathVariable orderId: String,
        @RequestBody request: AddOrderItemRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val merchantUserId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.addItem(orderId, request, merchantUserId))
    }

    @Operation(summary = "Update quantity of an item in an unpaid merchant order")
    @PatchMapping("/{orderId}/items/{itemId}")
    fun updateItem(
        @PathVariable orderId: String,
        @PathVariable itemId: UUID,
        @RequestBody request: UpdateOrderItemRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val merchantUserId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.updateItemQuantity(orderId, itemId, request, merchantUserId))
    }

    @Operation(summary = "Remove an item from an unpaid merchant order (auto-cancels if last item)")
    @DeleteMapping("/{orderId}/items/{itemId}")
    fun removeItem(
        @PathVariable orderId: String,
        @PathVariable itemId: UUID,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val merchantUserId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.removeItem(orderId, itemId, merchantUserId))
    }

    @Operation(summary = "Cancel an unpaid merchant order")
    @PostMapping("/{orderId}/cancel")
    fun cancel(
        @PathVariable orderId: String,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val merchantUserId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.cancelOrder(orderId, merchantUserId))
    }

    @Operation(summary = "Apply a merchant price override to a specific order item")
    @PatchMapping("/{orderId}/items/{itemId}/price")
    fun overrideItemPrice(
        @PathVariable orderId: String,
        @PathVariable itemId: UUID,
        @RequestBody request: MerchantItemPriceOverrideRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val merchantUserId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.applyMerchantPriceOverride(orderId, itemId, request, merchantUserId))
    }

    @Operation(summary = "Manually settle an unpaid or failed order")
    @PostMapping("/{orderId}/settle")
    fun settle(
        @PathVariable orderId: String,
        @RequestBody request: SettleOrderRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<OrderResponse> {
        val merchantUserId = requireSubject(authorization)
        return ResponseEntity.ok(orderService.manualSettle(orderId, request, merchantUserId))
    }

    @Operation(summary = "Get a specific order by orderId for the merchant")
    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): ResponseEntity<OrderResponse> =
        ResponseEntity.ok(orderService.getMerchantOrder(orderId))

    @Operation(summary = "List past customers (shoppers) who have ordered at the given branch")
    @GetMapping("/branches/{branchId}/customers")
    fun listBranchCustomers(@PathVariable branchId: UUID): ResponseEntity<List<BranchCustomerResponse>> =
        ResponseEntity.ok(orderService.listBranchCustomers(branchId))

    @Operation(summary = "Search for a shopper by email address")
    @GetMapping("/shoppers/search")
    fun searchShopper(@RequestParam email: String): ResponseEntity<ShopperSearchResult> {
        val result = shopperProfileGateway.findByEmail(email)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
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
