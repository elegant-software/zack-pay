package com.elegant.software.blitzpay.order.web

import com.elegant.software.blitzpay.config.KioskAuthentication
import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.STOREFRONT_GUEST_TOKEN_COOKIE
import com.elegant.software.blitzpay.order.api.StorefrontOrderRequest
import com.elegant.software.blitzpay.order.service.OrderService
import com.elegant.software.blitzpay.order.service.StorefrontOrderBus
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Anonymous web storefront checkout. Ownership of a guest order is proven by the `guest_token` session cookie
 * (HttpOnly, order-scoped, expires server-side after [com.elegant.software.blitzpay.order.service.GuestToken.TTL]).
 */
@Tag(name = "Storefront Orders", description = "Guest orders for the anonymous web storefront")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/storefront/orders", version = "1")
class StorefrontOrderController(
    private val orderService: OrderService,
    private val bus: StorefrontOrderBus,
) {
    private val log = LoggerFactory.getLogger(StorefrontOrderController::class.java)

    @Operation(summary = "Create a guest order priced from the catalog; sets the guest_token session cookie")
    @PostMapping
    fun create(
        @RequestBody request: StorefrontOrderRequest,
        authentication: Authentication?,
    ): ResponseEntity<OrderResponse> {
        // A valid Kiosk token (see config.KioskTokenAuthenticationFilter) marks the Order as Kiosk-originated.
        val kiosk = (authentication as? KioskAuthentication)?.principal
        val created = orderService.createGuestOrder(request, kiosk)
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

    @Operation(
        summary = "Stream a guest order as SSE: an `order.changed` event (id = orderId, data = OrderResponse) right " +
            "after connecting and after every Preparation Status or payment status change; requires the guest_token cookie",
    )
    @GetMapping("/{orderId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @PathVariable orderId: String,
        @CookieValue(name = STOREFRONT_GUEST_TOKEN_COOKIE, required = false) token: String?,
        response: HttpServletResponse,
    ): SseEmitter {
        // Checked before the stream opens, so an unknown order or bad token is a plain 404.
        val current = orderService.getGuestOrder(orderId, token)
        // nginx (storefront proxy and ingress) buffers responses by default, which would hold events back.
        response.setHeader("X-Accel-Buffering", "no")
        return SseEmitter(STREAM_TIMEOUT_MS).also {
            bus.register(orderId, it)
            // The snapshot doubles as the initial flush, so the client needs no separate GET.
            it.send(StorefrontOrderBus.changedEvent(current))
        }
    }

    companion object {
        /** Clients reconnect after this (and get a fresh snapshot); heartbeats keep the connection alive meanwhile. */
        private const val STREAM_TIMEOUT_MS = 60 * 60 * 1000L

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
