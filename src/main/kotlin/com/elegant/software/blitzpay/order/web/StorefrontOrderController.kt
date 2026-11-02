package com.elegant.software.blitzpay.order.web

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.STOREFRONT_GUEST_TOKEN_COOKIE
import com.elegant.software.blitzpay.order.api.StorefrontOrderRequest
import com.elegant.software.blitzpay.order.service.OrderCreationConflictException
import com.elegant.software.blitzpay.order.service.OrderService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Anonymous web storefront checkout. Ownership of a guest order is proven by the `guest_token` session cookie
 * (HttpOnly, order-scoped, expires server-side after [com.elegant.software.blitzpay.order.service.GuestToken.TTL]).
 */
@Tag(name = "Storefront Orders", description = "Guest orders for the anonymous web storefront")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/storefront/orders", version = "1")
class StorefrontOrderController(private val orderService: OrderService) {
    private val log = LoggerFactory.getLogger(StorefrontOrderController::class.java)

    @Operation(summary = "Create a guest order priced from the catalog; sets the guest_token session cookie")
    @PostMapping
    fun create(@RequestBody request: StorefrontOrderRequest): ResponseEntity<OrderResponse> {
        val created = orderService.createGuestOrder(request)
        LogContext.with(LogContext.ORDER_ID to created.order.orderId) {
            log.info("guest order created totalAmountMinor={} itemCount={}", created.order.totalAmountMinor, created.order.items.size)
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, guestTokenCookie(created.token).toString())
            .body(created.order)
    }

    @Operation(summary = "Get a guest order; requires the guest_token cookie issued for this order")
    @GetMapping("/{orderId}")
    fun get(
        @PathVariable orderId: String,
        @CookieValue(name = STOREFRONT_GUEST_TOKEN_COOKIE, required = false) token: String?,
    ): ResponseEntity<OrderResponse> = ResponseEntity.ok(orderService.getGuestOrder(orderId, token))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(ex: IllegalArgumentException): ResponseEntity<OrderErrorResponse> =
        ResponseEntity.badRequest().body(OrderErrorResponse(ex.message ?: "Invalid order request"))

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(ex: NoSuchElementException): ResponseEntity<OrderErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(OrderErrorResponse(ex.message ?: "Order not found"))

    @ExceptionHandler(OrderCreationConflictException::class)
    fun unprocessable(ex: OrderCreationConflictException): ResponseEntity<OrderErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(OrderErrorResponse(ex.message ?: "Order cannot be created"))

    companion object {
        // No Max-Age: a session cookie the browser drops on close. The server-side TTL is the real bound.
        fun guestTokenCookie(token: String): ResponseCookie =
            ResponseCookie.from(STOREFRONT_GUEST_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/v1/storefront")
                .build()
    }
}
