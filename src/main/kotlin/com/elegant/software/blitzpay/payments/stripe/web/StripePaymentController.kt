package com.elegant.software.blitzpay.payments.stripe.web

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.order.api.OrderPaymentSummary
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.outbound.StripePaymentService
import com.stripe.exception.StripeException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.slf4j.LoggerFactory
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

data class ErrorResponse(val error: String)

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments/stripe", version = "1")
class StripePaymentController(
    private val stripePaymentService: StripePaymentService,
    private val credentialResolver: MerchantCredentialResolver,
    private val stripeConnectGateway: StripeConnectGateway,
    private val stripeProperties: StripeProperties,
    private val orderGateway: OrderGateway,
    private val paymentStatusInitializationGateway: PaymentStatusInitializationGateway,
) {
    private val log = LoggerFactory.getLogger(StripePaymentController::class.java)

    @Operation(summary = "Create a Stripe PaymentIntent and return credentials to the mobile SDK.")
    @ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = CreateIntentResponse::class))])
    @PostMapping("/create-intent")
    fun createIntent(@RequestBody request: CreateIntentRequest): ResponseEntity<Any> {
        val paymentContext = try {
            validateStripePaymentRequest(request.merchantId, request.orderId, request.branchId, request.productId)
        } catch (ex: StripeOrderValidationException) {
            return ResponseEntity.status(ex.status).body(ErrorResponse(ex.message))
        } catch (ex: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request"))
        }
        val (merchantId, orderId, orderSummary, resolvedBranchId) = paymentContext
        if (merchantId != orderSummary.merchantId) {
            return ResponseEntity.badRequest().body(ErrorResponse("merchantId does not match the order"))
        }
        val resolvedAmount = resolveAmount(request, orderSummary.totalAmountMinor)
            ?: return ResponseEntity.badRequest().body(ErrorResponse("amount must be a positive number"))
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
            ?.takeIf { stripeConnectGateway.isChargesEnabled(merchantId) }
            ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse("Stripe Connect account not configured or not yet verified for this merchant"))
        val paymentMethodType = request.paymentMethodType ?: "card"
        if (paymentMethodType == "card_present" && !stripeConnectGateway.isTapToPayEnabled(merchantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("Tap to Pay is not enabled for this merchant"))
        }
        val currency = request.currency ?: "eur"
        val paymentRequestId = UUID.randomUUID()
        return LogContext.with(
            LogContext.ORDER_ID to orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "STRIPE",
        ) {
            log.info("stripe create_intent request merchantId={} branchId={} amount={} currency={} productId={}", merchantId, resolvedBranchId, resolvedAmount, currency.uppercase(), request.productId)
            paymentStatusInitializationGateway.initialize(paymentRequestId, null, orderId, Math.round(resolvedAmount * 100), currency.uppercase())
            log.info("stripe payment status initialized")
            val paymentAttemptProvider = if (paymentMethodType == "card_present") "STRIPE_TERMINAL" else "STRIPE"
            orderGateway.linkPaymentAttempt(orderId, paymentRequestId, paymentAttemptProvider, null)
            log.info("stripe payment attempt linked to order")
            try {
                val result = stripePaymentService.createIntent(resolvedAmount, currency, connectedAccountId, merchantId, resolvedBranchId, orderId, paymentRequestId, request.productId, paymentMethodType)
                LogContext.with(LogContext.PAYMENT_INTENT_ID to result.paymentIntentId) {
                    log.info("stripe create_intent succeeded connectedAccountId={}", result.connectedAccountId)
                }
                ResponseEntity.ok(
                    CreateIntentResponse(paymentRequestId, result.clientSecret, result.paymentIntentId, stripeProperties.publishableKey, result.connectedAccountId) as Any
                )
            } catch (ex: IllegalArgumentException) {
                log.warn("stripe create_intent rejected error={}", ex.message)
                ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request") as Any)
            } catch (ex: StripeException) {
                log.error("stripe create_intent failed code={} message={}", ex.code, ex.message)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(ex.message ?: "Stripe error") as Any)
            }
        }
    }

    @Operation(summary = "Create a hosted Stripe Checkout Session for Pay by Bank and return the redirect URL.")
    @ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = CreatePayByBankCheckoutSessionResponse::class))])
    @PostMapping("/pay-by-bank/create-checkout-session")
    fun createPayByBankCheckoutSession(@RequestBody request: CreatePayByBankCheckoutSessionRequest): ResponseEntity<Any> {
        val paymentContext = try {
            validateStripePaymentRequest(request.merchantId, request.orderId, request.branchId, request.productId)
        } catch (ex: StripeOrderValidationException) {
            return ResponseEntity.status(ex.status).body(ErrorResponse(ex.message))
        } catch (ex: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request"))
        }
        val (merchantId, orderId, orderSummary, resolvedBranchId) = paymentContext
        if (merchantId != orderSummary.merchantId) {
            return ResponseEntity.badRequest().body(ErrorResponse("merchantId does not match the order"))
        }
        val resolvedAmount = resolveAmount(request.amount, request.productId, orderSummary.totalAmountMinor)
            ?: return ResponseEntity.badRequest().body(ErrorResponse("amount must be a positive number"))
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
            ?.takeIf { stripeConnectGateway.isPayByBankEnabled(merchantId) }
            ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse("Stripe Pay by Bank is not configured or not yet enabled for this merchant"))
        val currency = (request.currency ?: orderSummary.currency).lowercase()
        if (currency !in setOf("eur", "gbp")) {
            return ResponseEntity.badRequest().body(ErrorResponse("Stripe Pay by Bank supports only EUR and GBP"))
        }
        val successUrl = request.successUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: stripeProperties.connect.appDeepLink.takeIf { it.isNotBlank() }
            ?: return ResponseEntity.badRequest().body(ErrorResponse("successUrl is required when STRIPE_CONNECT_APP_DEEP_LINK is not configured"))
        val cancelUrl = request.cancelUrl?.trim()?.takeIf { it.isNotEmpty() } ?: successUrl
        val paymentRequestId = UUID.randomUUID()
        val statementDescriptor = statementDescriptorForMerchant(merchantId)
        return LogContext.with(
            LogContext.ORDER_ID to orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "STRIPE",
        ) {
            log.info("stripe pay_by_bank checkout session request merchantId={} branchId={} amount={} currency={} productId={}", merchantId, resolvedBranchId, resolvedAmount, currency.uppercase(), request.productId)
            paymentStatusInitializationGateway.initialize(paymentRequestId, null, orderId, Math.round(resolvedAmount * 100), currency.uppercase())
            log.info("stripe pay_by_bank payment status initialized")
            orderGateway.linkPaymentAttempt(orderId, paymentRequestId, "STRIPE_PAY_BY_BANK", null)
            log.info("stripe pay_by_bank payment attempt linked to order")
            try {
                val result = stripePaymentService.createPayByBankCheckoutSession(resolvedAmount, currency, connectedAccountId, merchantId, resolvedBranchId, orderId, paymentRequestId, request.productId, successUrl, cancelUrl, statementDescriptor)
                orderGateway.linkPaymentAttempt(orderId, paymentRequestId, "STRIPE_PAY_BY_BANK", result.checkoutSessionId)
                log.info("stripe pay_by_bank checkout session created sessionId={}", result.checkoutSessionId)
                ResponseEntity.ok(
                    CreatePayByBankCheckoutSessionResponse(paymentRequestId, result.checkoutUrl, result.checkoutSessionId, result.connectedAccountId) as Any
                )
            } catch (ex: IllegalArgumentException) {
                log.warn("stripe pay_by_bank checkout session rejected error={}", ex.message)
                ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request") as Any)
            } catch (ex: StripeException) {
                log.error("stripe pay_by_bank checkout session failed code={} message={}", ex.code, ex.message)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(ex.message ?: "Stripe error") as Any)
            }
        }
    }

    private fun resolveAmount(request: CreateIntentRequest, orderAmountMinor: Long): Double? =
        resolveAmount(request.amount, request.productId, orderAmountMinor)

    private fun resolveAmount(amount: Double?, productId: UUID?, orderAmountMinor: Long): Double? {
        val productPrice = productId?.let { credentialResolver.resolveProductPrice(it) }
        val effectiveAmount = when {
            amount != null && productPrice != null -> {
                if (amount != productPrice.toDouble()) {
                    log.warn("stripe create_intent amount override productId={} requestAmount={} productPrice={}", productId, amount, productPrice.toDouble())
                }
                amount
            }
            amount != null -> amount
            productPrice != null -> productPrice.toDouble()
            else -> orderAmountMinor / 100.0
        }
        return effectiveAmount.takeIf { it > 0 && it.isFinite() }
    }

    private fun validateStripePaymentRequest(merchantId: UUID?, orderId: String, branchId: UUID?, productId: UUID?): StripePaymentContext {
        val resolvedMerchantId = merchantId ?: throw IllegalArgumentException("merchantId is required")
        val resolvedOrderId = orderId.trim().takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("orderId must not be blank")
        val orderSummary = try {
            orderGateway.assertPayable(resolvedOrderId)
        } catch (ex: NoSuchElementException) {
            throw StripeOrderValidationException(HttpStatus.NOT_FOUND, ex.message ?: "Order not found")
        } catch (ex: IllegalStateException) {
            throw StripeOrderValidationException(HttpStatus.CONFLICT, ex.message ?: "Order is not payable")
        }
        val resolvedBranchId = credentialResolver.resolveBranch(resolvedMerchantId, branchId ?: orderSummary.branchId, productId)
            ?: throw IllegalArgumentException("branch cannot be resolved: supply branchId or productId")
        return StripePaymentContext(resolvedMerchantId, resolvedOrderId, orderSummary, resolvedBranchId)
    }

    private fun statementDescriptorForMerchant(merchantId: UUID): String =
        stripeConnectGateway.getDetails(merchantId).merchantName
            .uppercase()
            .replace(Regex("[^A-Z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(22)
            .ifBlank { "BLITZPAY ORDER" }

    private data class StripePaymentContext(val merchantId: UUID, val orderId: String, val orderSummary: OrderPaymentSummary, val resolvedBranchId: UUID)
    private class StripeOrderValidationException(val status: HttpStatus, override val message: String) : RuntimeException(message)
}
