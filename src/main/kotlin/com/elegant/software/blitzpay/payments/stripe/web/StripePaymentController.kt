package com.elegant.software.blitzpay.payments.stripe.web

import com.elegant.software.blitzpay.config.ApiException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateIntentRequest(
    val amount: Double? = null,
    val currency: String? = null,
    val orderId: String,
    val merchantId: UUID? = null,
    val branchId: UUID? = null,
    val productId: UUID? = null,
    val paymentMethodType: String? = null,
)

data class CreateIntentResponse(
    val paymentRequestId: UUID,
    val clientSecret: String,
    val paymentIntent: String,
    val publishableKey: String,
    val connectedAccountId: String,
)

data class CreatePayByBankCheckoutSessionRequest(
    val amount: Double? = null,
    val currency: String? = null,
    val orderId: String,
    val merchantId: UUID? = null,
    val branchId: UUID? = null,
    val productId: UUID? = null,
    val successUrl: String? = null,
    val cancelUrl: String? = null,
)

data class CreatePayByBankCheckoutSessionResponse(
    val paymentRequestId: UUID,
    val checkoutUrl: String,
    val checkoutSessionId: String,
    val connectedAccountId: String,
)


@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments/stripe", version = "1")
class StripePaymentController(private val stripeCheckoutService: StripeCheckoutService) {

    @Operation(summary = "Create a Stripe PaymentIntent and return credentials to the mobile SDK.")
    @ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = CreateIntentResponse::class))])
    @PostMapping("/create-intent")
    fun createIntent(@RequestBody request: CreateIntentRequest): ResponseEntity<Any> {
        val merchantId = request.merchantId ?: throw ApiException(HttpStatus.BAD_REQUEST, "merchantId is required")
        val target = StripeCheckoutTarget(request.orderId, merchantId, request.branchId, request.productId, request.amount)
        return ResponseEntity.ok(stripeCheckoutService.createIntent(target, request.paymentMethodType ?: "card"))
    }

    @Operation(summary = "Create a hosted Stripe Checkout Session for Pay by Bank and return the redirect URL.")
    @ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = CreatePayByBankCheckoutSessionResponse::class))])
    @PostMapping("/pay-by-bank/create-checkout-session")
    fun createPayByBankCheckoutSession(@RequestBody request: CreatePayByBankCheckoutSessionRequest): ResponseEntity<Any> {
        val merchantId = request.merchantId ?: throw ApiException(HttpStatus.BAD_REQUEST, "merchantId is required")
        val target = StripeCheckoutTarget(request.orderId, merchantId, request.branchId, request.productId, request.amount)
        return ResponseEntity.ok(
            stripeCheckoutService.createPayByBankCheckoutSession(target, { request.successUrl }, { request.cancelUrl })
        )
    }
}
