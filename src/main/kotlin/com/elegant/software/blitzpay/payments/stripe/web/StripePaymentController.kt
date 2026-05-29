package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.stripe.exception.StripeException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
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
    private val stripeProperties: com.elegant.software.blitzpay.payments.stripe.config.StripeProperties,
    private val orderGateway: OrderGateway,
    private val paymentStatusInitializationGateway: PaymentStatusInitializationGateway,
) {
    private val log = LoggerFactory.getLogger(StripePaymentController::class.java)

    @Operation(summary = "Create a Stripe PaymentIntent and return credentials to the mobile SDK.")
    @ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = CreateIntentResponse::class))])
    @PostMapping("/create-intent")
    fun createIntent(@RequestBody request: CreateIntentRequest): Mono<ResponseEntity<Any>> {
        val paymentContext = try {
            validateStripePaymentRequest(
                merchantId = request.merchantId,
                orderId = request.orderId,
                branchId = request.branchId,
                productId = request.productId,
            )
        } catch (ex: StripeOrderValidationException) {
            return Mono.just(ResponseEntity.status(ex.status).body(ErrorResponse(ex.message) as Any))
        } catch (ex: IllegalArgumentException) {
            return Mono.just(ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request") as Any))
        }
        val (merchantId, orderId, orderSummary, resolvedBranchId) = paymentContext
        if (merchantId != orderSummary.merchantId) {
            return Mono.just(ResponseEntity.badRequest().body(ErrorResponse("merchantId does not match the order") as Any))
        }

        // Resolve amount: explicit amount overrides product price; log WARN on mismatch
        val resolvedAmount = resolveAmount(request, orderSummary.totalAmountMinor) ?: return Mono.just(
            ResponseEntity.badRequest().body(ErrorResponse("amount must be a positive number") as Any)
        )

        // Resolve Stripe Connect — merchant must have a verified Connected Account
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
            ?.takeIf { stripeConnectGateway.isChargesEnabled(merchantId) }
            ?: return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse("Stripe Connect account not configured or not yet verified for this merchant") as Any)
            )

        val paymentMethodType = request.paymentMethodType ?: "card"
        if (paymentMethodType == "card_present" && !stripeConnectGateway.isTapToPayEnabled(merchantId)) {
            return Mono.just(
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse("Tap to Pay is not enabled for this merchant") as Any)
            )
        }

        val currency = request.currency ?: "eur"
        val paymentRequestId = UUID.randomUUID()
        return LogContext.with(
            LogContext.ORDER_ID to orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "STRIPE",
        ) {
            log.info(
                "stripe create_intent request merchantId={} branchId={} amount={} currency={} productId={}",
                merchantId, resolvedBranchId, resolvedAmount, currency.uppercase(), request.productId,
            )
            paymentStatusInitializationGateway.initialize(
                paymentRequestId = paymentRequestId,
                payerRef = null,
                orderId = orderId,
                amountMinorUnits = Math.round(resolvedAmount * 100),
                currency = currency.uppercase(),
            )
            log.info("stripe payment status initialized")
            val paymentAttemptProvider = if (paymentMethodType == "card_present") "STRIPE_TERMINAL" else "STRIPE"
            orderGateway.linkPaymentAttempt(orderId, paymentRequestId, paymentAttemptProvider, null)
            log.info("stripe payment attempt linked to order")
            stripePaymentService.createIntent(
                resolvedAmount,
                currency,
                connectedAccountId,
                merchantId,
                resolvedBranchId,
                orderId,
                paymentRequestId,
                request.productId,
                paymentMethodType,
            )
                .map { result ->
                    LogContext.with(LogContext.PAYMENT_INTENT_ID to result.paymentIntentId) {
                        log.info("stripe create_intent succeeded connectedAccountId={}", result.connectedAccountId)
                        ResponseEntity.ok(
                            CreateIntentResponse(
                                paymentRequestId = paymentRequestId,
                                clientSecret = result.clientSecret,
                                paymentIntent = result.paymentIntentId,
                                publishableKey = stripeProperties.publishableKey,
                                connectedAccountId = result.connectedAccountId,
                            ) as Any
                        )
                    }
                }
                .onErrorResume(IllegalArgumentException::class.java) { ex ->
                    log.warn("stripe create_intent rejected error={}", ex.message)
                    Mono.just(ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request") as Any))
                }
                .onErrorResume(StripeException::class.java) { ex ->
                    log.error("stripe create_intent failed code={} message={}", ex.code, ex.message)
                    Mono.just(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ErrorResponse(ex.message ?: "Stripe error") as Any)
                    )
                }
                .doOnSubscribe {
                    log.info("stripe create_intent dispatching to Stripe")
                }
                .doOnCancel {
                    log.warn("stripe create_intent cancelled before completion")
                }
                .doOnSuccess { response ->
                    log.info("stripe create_intent response status={}", response?.statusCode?.value())
                }
                .doOnError { ex ->
                    log.error("stripe create_intent reactive pipeline error={}", ex.message, ex)
                }
        }
    }

    @Operation(summary = "Create a hosted Stripe Checkout Session for Pay by Bank and return the redirect URL.")
    @ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = CreatePayByBankCheckoutSessionResponse::class))])
    @PostMapping("/pay-by-bank/create-checkout-session")
    fun createPayByBankCheckoutSession(@RequestBody request: CreatePayByBankCheckoutSessionRequest): Mono<ResponseEntity<Any>> {
        val paymentContext = try {
            validateStripePaymentRequest(
                merchantId = request.merchantId,
                orderId = request.orderId,
                branchId = request.branchId,
                productId = request.productId,
            )
        } catch (ex: StripeOrderValidationException) {
            return Mono.just(ResponseEntity.status(ex.status).body(ErrorResponse(ex.message) as Any))
        } catch (ex: IllegalArgumentException) {
            return Mono.just(ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request") as Any))
        }
        val (merchantId, orderId, orderSummary, resolvedBranchId) = paymentContext
        if (merchantId != orderSummary.merchantId) {
            return Mono.just(ResponseEntity.badRequest().body(ErrorResponse("merchantId does not match the order") as Any))
        }

        val resolvedAmount = resolveAmount(
            amount = request.amount,
            productId = request.productId,
            orderAmountMinor = orderSummary.totalAmountMinor,
        ) ?: return Mono.just(
            ResponseEntity.badRequest().body(ErrorResponse("amount must be a positive number") as Any)
        )

        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
            ?.takeIf { stripeConnectGateway.isPayByBankEnabled(merchantId) }
            ?: return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse("Stripe Pay by Bank is not configured or not yet enabled for this merchant") as Any)
            )

        val currency = (request.currency ?: orderSummary.currency).lowercase()
        if (currency !in setOf("eur", "gbp")) {
            return Mono.just(
                ResponseEntity.badRequest()
                    .body(ErrorResponse("Stripe Pay by Bank supports only EUR and GBP") as Any)
            )
        }

        val successUrl = request.successUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: stripeProperties.connect.appDeepLink.takeIf { it.isNotBlank() }
            ?: return Mono.just(
                ResponseEntity.badRequest()
                    .body(ErrorResponse("successUrl is required when STRIPE_CONNECT_APP_DEEP_LINK is not configured") as Any)
            )
        val cancelUrl = request.cancelUrl?.trim()?.takeIf { it.isNotEmpty() } ?: successUrl

        val paymentRequestId = UUID.randomUUID()
        val statementDescriptor = statementDescriptorForMerchant(merchantId)
        return LogContext.with(
            LogContext.ORDER_ID to orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "STRIPE",
        ) {
            log.info(
                "stripe pay_by_bank checkout session request merchantId={} branchId={} amount={} currency={} productId={}",
                merchantId, resolvedBranchId, resolvedAmount, currency.uppercase(), request.productId,
            )
            paymentStatusInitializationGateway.initialize(
                paymentRequestId = paymentRequestId,
                payerRef = null,
                orderId = orderId,
                amountMinorUnits = Math.round(resolvedAmount * 100),
                currency = currency.uppercase(),
            )
            log.info("stripe pay_by_bank payment status initialized")
            orderGateway.linkPaymentAttempt(orderId, paymentRequestId, "STRIPE_PAY_BY_BANK", null)
            log.info("stripe pay_by_bank payment attempt linked to order")
            stripePaymentService.createPayByBankCheckoutSession(
                amount = resolvedAmount,
                currency = currency,
                connectedAccountId = connectedAccountId,
                merchantId = merchantId,
                branchId = resolvedBranchId,
                orderId = orderId,
                paymentRequestId = paymentRequestId,
                productId = request.productId,
                successUrl = successUrl,
                cancelUrl = cancelUrl,
                statementDescriptor = statementDescriptor,
            )
                .map { result ->
                    orderGateway.linkPaymentAttempt(orderId, paymentRequestId, "STRIPE_PAY_BY_BANK", result.checkoutSessionId)
                    log.info("stripe pay_by_bank checkout session created sessionId={}", result.checkoutSessionId)
                    ResponseEntity.ok(
                        CreatePayByBankCheckoutSessionResponse(
                            paymentRequestId = paymentRequestId,
                            checkoutUrl = result.checkoutUrl,
                            checkoutSessionId = result.checkoutSessionId,
                            connectedAccountId = result.connectedAccountId,
                        ) as Any
                    )
                }
                .onErrorResume(IllegalArgumentException::class.java) { ex ->
                    log.warn("stripe pay_by_bank checkout session rejected error={}", ex.message)
                    Mono.just(ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request") as Any))
                }
                .onErrorResume(StripeException::class.java) { ex ->
                    log.error("stripe pay_by_bank checkout session failed code={} message={}", ex.code, ex.message)
                    Mono.just(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ErrorResponse(ex.message ?: "Stripe error") as Any)
                    )
                }
        }
    }

    private fun resolveAmount(request: CreateIntentRequest, orderAmountMinor: Long): Double? {
        return resolveAmount(request.amount, request.productId, orderAmountMinor)
    }

    private fun resolveAmount(amount: Double?, productId: UUID?, orderAmountMinor: Long): Double? {
        val productPrice = productId?.let { credentialResolver.resolveProductPrice(it) }
        val effectiveAmount = when {
            amount != null && productPrice != null -> {
                val requestedDouble = amount
                val priceDouble = productPrice.toDouble()
                if (requestedDouble != priceDouble) {
                    log.warn(
                        "stripe create_intent amount override productId={} requestAmount={} productPrice={}",
                        productId, requestedDouble, priceDouble,
                    )
                }
                requestedDouble
            }
            amount != null -> amount
            productPrice != null -> productPrice.toDouble()
            else -> orderAmountMinor / 100.0
        }
        return effectiveAmount.takeIf { it > 0 && it.isFinite() }
    }

    private fun validateStripePaymentRequest(
        merchantId: UUID?,
        orderId: String,
        branchId: UUID?,
        productId: UUID?,
    ): StripePaymentContext {
        val resolvedMerchantId = merchantId ?: throw IllegalArgumentException("merchantId is required")
        val resolvedOrderId = orderId.trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("orderId must not be blank")
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

    private fun statementDescriptorForMerchant(merchantId: UUID): String {
        val merchantName = stripeConnectGateway.getDetails(merchantId).merchantName
        return merchantName
            .uppercase()
            .replace(Regex("[^A-Z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(22)
            .ifBlank { "BLITZPAY ORDER" }
    }

    private data class StripePaymentContext(
        val merchantId: UUID,
        val orderId: String,
        val orderSummary: com.elegant.software.blitzpay.order.api.OrderPaymentSummary,
        val resolvedBranchId: UUID,
    )

    private class StripeOrderValidationException(val status: HttpStatus, override val message: String) : RuntimeException(message)
}
