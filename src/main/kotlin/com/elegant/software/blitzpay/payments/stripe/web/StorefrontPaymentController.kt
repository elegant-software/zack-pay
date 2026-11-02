package com.elegant.software.blitzpay.payments.stripe.web

import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.STOREFRONT_GUEST_TOKEN_COOKIE
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class StorefrontPaymentMethod { CARD, PAY_BY_BANK }

data class StorefrontPaymentRequest(val method: StorefrontPaymentMethod)

/** CARD: [clientSecret] + [publishableKey] + [connectedAccountId] for the Stripe.js Payment Element. PAY_BY_BANK: [checkoutUrl]. */
data class StorefrontPaymentResponse(
    val method: StorefrontPaymentMethod,
    val connectedAccountId: String,
    val clientSecret: String? = null,
    val publishableKey: String? = null,
    val checkoutUrl: String? = null,
)

/**
 * Guest payment for web storefront orders. Authorised only by the order-scoped `guest_token` cookie; the amount,
 * merchant, branch and redirect URLs all come from the order and server config, never from the request.
 */
@Tag(name = "Storefront Payments", description = "Stripe payment for anonymous web storefront orders")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/storefront/orders/{orderId}/payment", version = "1")
class StorefrontPaymentController(
    private val stripeCheckoutService: StripeCheckoutService,
    private val orderGateway: OrderGateway,
    private val stripeProperties: StripeProperties,
) {
    private val log = LoggerFactory.getLogger(StorefrontPaymentController::class.java)

    @Operation(summary = "Start Stripe payment for a guest order; requires the guest_token cookie issued for this order")
    @PostMapping
    fun pay(
        @PathVariable orderId: String,
        @RequestBody request: StorefrontPaymentRequest,
        @CookieValue(name = STOREFRONT_GUEST_TOKEN_COOKIE, required = false) token: String?,
    ): ResponseEntity<Any> {
        // Same 404 as an unknown order, so the endpoint is not an order-ID oracle.
        if (!orderGateway.isGuestTokenValid(orderId, token)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("Order not found: $orderId"))
        }
        // ponytail: per-order cap limits card testing through one cookie; per-IP limits + captcha sit in front of order creation.
        if (orderGateway.countPaymentAttempts(orderId) >= MAX_PAYMENT_ATTEMPTS) {
            log.warn("storefront payment attempt limit reached orderId={}", orderId)
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ErrorResponse("Too many payment attempts for this order"))
        }
        val target = StripeCheckoutTarget(orderId)
        val response = when (request.method) {
            StorefrontPaymentMethod.CARD -> stripeCheckoutService.createIntent(target, "card").let {
                StorefrontPaymentResponse(request.method, it.connectedAccountId, clientSecret = it.clientSecret, publishableKey = it.publishableKey)
            }
            StorefrontPaymentMethod.PAY_BY_BANK -> {
                val returnUrl = confirmationUrl(orderId)
                stripeCheckoutService.createPayByBankCheckoutSession(target, { returnUrl }, { returnUrl }).let {
                    StorefrontPaymentResponse(request.method, it.connectedAccountId, checkoutUrl = it.checkoutUrl)
                }
            }
        }
        return ResponseEntity.ok(response)
    }

    private fun confirmationUrl(orderId: String): String {
        val base = stripeProperties.storefrontUrl.trimEnd('/').takeIf { it.isNotBlank() }
            ?: throw StripeCheckoutException(HttpStatus.SERVICE_UNAVAILABLE, "STOREFRONT_URL is not configured")
        return "$base/order/${URLEncoder.encode(orderId, StandardCharsets.UTF_8)}/confirmation"
    }

    @ExceptionHandler(StripeCheckoutException::class)
    fun checkoutFailed(ex: StripeCheckoutException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status).body(ErrorResponse(ex.message))

    private companion object {
        const val MAX_PAYMENT_ATTEMPTS = 5L
    }
}
