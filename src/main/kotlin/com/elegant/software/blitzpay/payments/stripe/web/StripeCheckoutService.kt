package com.elegant.software.blitzpay.payments.stripe.web

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderPaymentSummary
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.outbound.StripePaymentService
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

/** What to charge. The order total is always the amount; [merchantId] null means "the order's merchant". */
data class StripeCheckoutTarget(
    val orderId: String,
    val merchantId: UUID? = null,
    val branchId: UUID? = null,
    val productId: UUID? = null,
    val amount: Double? = null,
)

class StripeCheckoutException(status: HttpStatus, override val message: String, code: String = status.name) : ApiException(status, message, code)

/** Stripe PaymentIntent / Pay by Bank Checkout Session creation shared by the shopper app and the web storefront. */
@Service
class StripeCheckoutService(
    private val stripePaymentService: StripePaymentService,
    private val credentialResolver: MerchantCredentialResolver,
    private val stripeConnectGateway: StripeConnectGateway,
    private val stripeProperties: StripeProperties,
    private val orderGateway: OrderGateway,
    private val paymentStatusInitializationGateway: PaymentStatusInitializationGateway,
) {
    private val log = LoggerFactory.getLogger(StripeCheckoutService::class.java)

    fun createIntent(target: StripeCheckoutTarget, paymentMethodType: String = "card"): CreateIntentResponse {
        val (merchantId, orderId, orderSummary, resolvedBranchId) = validate(target)
        val resolvedAmount = resolveAmount(target.amount, target.productId, orderSummary.totalAmountMinor)
            ?: throw StripeCheckoutException(HttpStatus.BAD_REQUEST, "amount must be a positive number")
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
            ?.takeIf { stripeConnectGateway.isChargesEnabled(merchantId) }
            ?: throw StripeCheckoutException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe Connect account not configured or not yet verified for this merchant")
        if (paymentMethodType == "card_present" && !stripeConnectGateway.isTapToPayEnabled(merchantId)) {
            throw StripeCheckoutException(HttpStatus.FORBIDDEN, "Terminal payments are not enabled for this merchant")
        }
        val currency = orderSummary.currency.lowercase()
        if (paymentMethodType == "card_present") {
            reusableTerminalIntent(orderId, connectedAccountId, orderSummary)?.let { return it }
        }
        val paymentRequestId = UUID.randomUUID()
        return LogContext.with(
            LogContext.ORDER_ID to orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "STRIPE",
        ) {
            log.info("stripe create_intent request merchantId={} branchId={} amount={} currency={} productId={}", merchantId, resolvedBranchId, resolvedAmount, currency.uppercase(), target.productId)
            paymentStatusInitializationGateway.initialize(paymentRequestId, null, orderId, Math.round(resolvedAmount * 100), currency.uppercase())
            log.info("stripe payment status initialized")
            val paymentAttemptProvider = if (paymentMethodType == "card_present") "STRIPE_TERMINAL" else "STRIPE"
            orderGateway.linkPaymentAttempt(orderId, paymentRequestId, paymentAttemptProvider, null)
            log.info("stripe payment attempt linked to order")
            stripeCall("create_intent") {
                val result = stripePaymentService.createIntent(resolvedAmount, currency, connectedAccountId, merchantId, resolvedBranchId, orderId, paymentRequestId, target.productId, paymentMethodType)
                if (paymentMethodType == "card_present") {
                    // The PaymentIntent id lets a retry find and reuse this open Terminal Payment.
                    orderGateway.linkPaymentAttempt(orderId, paymentRequestId, paymentAttemptProvider, result.paymentIntentId)
                }
                LogContext.with(LogContext.PAYMENT_INTENT_ID to result.paymentIntentId) {
                    log.info("stripe create_intent succeeded connectedAccountId={}", result.connectedAccountId)
                }
                CreateIntentResponse(paymentRequestId, result.clientSecret, result.paymentIntentId, stripeProperties.publishableKey, result.connectedAccountId)
            }
        }
    }

    /**
     * One open Terminal Payment per Order: when the Order's latest attempt is a Terminal PaymentIntent that can still be
     * collected, the app gets that same PaymentIntent back. A succeeded one means the Order is paid (webhook pending);
     * a canceled one, or one for a different amount (the Order changed since), yields null so a new one is created.
     */
    private fun reusableTerminalIntent(orderId: String, connectedAccountId: String, orderSummary: OrderPaymentSummary): CreateIntentResponse? {
        val attempt = orderGateway.findLastPaymentAttempt(orderId)
            ?.takeIf { it.provider == "STRIPE_TERMINAL" }
            ?: return null
        val paymentIntentId = attempt.providerReference ?: return null
        return LogContext.with(
            LogContext.ORDER_ID to orderId,
            LogContext.PAYMENT_REQUEST_ID to attempt.paymentRequestId,
            LogContext.PAYMENT_INTENT_ID to paymentIntentId,
            LogContext.PROVIDER to "STRIPE",
        ) {
            val intent = stripeCall("retrieve_intent") { stripePaymentService.retrieveIntent(paymentIntentId, connectedAccountId) }
            when {
                intent.status == "succeeded" -> {
                    log.info("stripe terminal intent already succeeded, rejecting new collection")
                    throw StripeCheckoutException(HttpStatus.CONFLICT, "Order is already paid: $orderId", "ORDER_ALREADY_PAID")
                }
                intent.status == "canceled" -> {
                    log.info("stripe terminal intent canceled, creating a new one")
                    null
                }
                intent.amount != orderSummary.totalAmountMinor || !intent.currency.equals(orderSummary.currency, ignoreCase = true) -> {
                    // Only an intent with nothing collected may be replaced; otherwise a second charge could follow.
                    if (intent.status != "requires_payment_method") {
                        log.warn("stripe terminal intent amount differs but collection in progress status={}", intent.status)
                        throw StripeCheckoutException(HttpStatus.CONFLICT, "A terminal payment is in progress for order $orderId", "PAYMENT_IN_PROGRESS")
                    }
                    log.warn("stripe terminal intent amount differs from order, canceling it intentAmount={} orderAmount={}", intent.amount, orderSummary.totalAmountMinor)
                    stripeCall("cancel_intent") { stripePaymentService.cancelIntent(paymentIntentId, connectedAccountId) }
                    null
                }
                else -> {
                    log.info("stripe terminal intent reused status={}", intent.status)
                    CreateIntentResponse(
                        attempt.paymentRequestId,
                        requireNotNull(intent.clientSecret) { "Stripe returned null clientSecret" },
                        intent.id,
                        stripeProperties.publishableKey,
                        connectedAccountId,
                    )
                }
            }
        }
    }

    /** [successUrl] / [cancelUrl] receive the order and return the redirect URL; null falls back to the app deep link. */
    fun createPayByBankCheckoutSession(
        target: StripeCheckoutTarget,
        successUrl: ((OrderPaymentSummary) -> String?)? = null,
        cancelUrl: ((OrderPaymentSummary) -> String?)? = null,
    ): CreatePayByBankCheckoutSessionResponse {
        val (merchantId, orderId, orderSummary, resolvedBranchId) = validate(target)
        val resolvedAmount = resolveAmount(target.amount, target.productId, orderSummary.totalAmountMinor)
            ?: throw StripeCheckoutException(HttpStatus.BAD_REQUEST, "amount must be a positive number")
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
            ?.takeIf { stripeConnectGateway.isPayByBankEnabled(merchantId) }
            ?: throw StripeCheckoutException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe Pay by Bank is not configured or not yet enabled for this merchant")
        val currency = orderSummary.currency.lowercase()
        if (currency !in setOf("eur", "gbp")) {
            throw StripeCheckoutException(HttpStatus.BAD_REQUEST, "Stripe Pay by Bank supports only EUR and GBP")
        }
        val resolvedSuccessUrl = successUrl?.invoke(orderSummary)?.trim()?.takeIf { it.isNotEmpty() }
            ?: stripeProperties.connect.appDeepLink.takeIf { it.isNotBlank() }
            ?: throw StripeCheckoutException(HttpStatus.BAD_REQUEST, "successUrl is required when STRIPE_CONNECT_APP_DEEP_LINK is not configured")
        val resolvedCancelUrl = cancelUrl?.invoke(orderSummary)?.trim()?.takeIf { it.isNotEmpty() } ?: resolvedSuccessUrl
        val paymentRequestId = UUID.randomUUID()
        val statementDescriptor = statementDescriptorForMerchant(merchantId)
        return LogContext.with(
            LogContext.ORDER_ID to orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "STRIPE",
        ) {
            log.info("stripe pay_by_bank checkout session request merchantId={} branchId={} amount={} currency={} productId={}", merchantId, resolvedBranchId, resolvedAmount, currency.uppercase(), target.productId)
            paymentStatusInitializationGateway.initialize(paymentRequestId, null, orderId, Math.round(resolvedAmount * 100), currency.uppercase())
            log.info("stripe pay_by_bank payment status initialized")
            orderGateway.linkPaymentAttempt(orderId, paymentRequestId, "STRIPE_PAY_BY_BANK", null)
            log.info("stripe pay_by_bank payment attempt linked to order")
            stripeCall("pay_by_bank checkout session") {
                val result = stripePaymentService.createPayByBankCheckoutSession(resolvedAmount, currency, connectedAccountId, merchantId, resolvedBranchId, orderId, paymentRequestId, target.productId, resolvedSuccessUrl, resolvedCancelUrl, statementDescriptor)
                orderGateway.linkPaymentAttempt(orderId, paymentRequestId, "STRIPE_PAY_BY_BANK", result.checkoutSessionId)
                log.info("stripe pay_by_bank checkout session created sessionId={}", result.checkoutSessionId)
                CreatePayByBankCheckoutSessionResponse(paymentRequestId, result.checkoutUrl, result.checkoutSessionId, result.connectedAccountId)
            }
        }
    }

    private fun <T> stripeCall(operation: String, block: () -> T): T =
        try {
            block()
        } catch (ex: IllegalArgumentException) {
            log.warn("stripe {} rejected error={}", operation, ex.message)
            throw StripeCheckoutException(HttpStatus.BAD_REQUEST, ex.message ?: "bad request")
        } catch (ex: StripeException) {
            log.error("stripe {} failed code={} message={}", operation, ex.code, ex.message)
            throw StripeCheckoutException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "Stripe error")
        }

    // The order total is the only trusted amount. Client-sent amount/productId are ignored for pricing
    // (kept in the request for backwards compatibility with older app builds).
    private fun resolveAmount(amount: Double?, productId: UUID?, orderAmountMinor: Long): Double? {
        // Malformed client amounts are still rejected (API contract), even though they never drive pricing.
        if (amount != null && !(amount > 0 && amount.isFinite())) return null
        val orderAmount = orderAmountMinor / 100.0
        if (amount != null && Math.round(amount * 100) != orderAmountMinor) {
            log.warn("stripe client amount ignored requestAmount={} orderAmount={} productId={}", amount, orderAmount, productId)
        }
        return orderAmount.takeIf { orderAmountMinor > 0 }
    }

    private fun validate(target: StripeCheckoutTarget): StripePaymentContext {
        val orderId = target.orderId.trim().takeIf { it.isNotEmpty() }
            ?: throw StripeCheckoutException(HttpStatus.BAD_REQUEST, "orderId must not be blank")
        val orderSummary = try {
            orderGateway.assertPayable(orderId)
        } catch (ex: NoSuchElementException) {
            throw StripeCheckoutException(HttpStatus.NOT_FOUND, ex.message ?: "Order not found")
        } catch (ex: IllegalStateException) {
            throw StripeCheckoutException(HttpStatus.CONFLICT, ex.message ?: "Order is not payable")
        } catch (ex: IllegalArgumentException) {
            throw StripeCheckoutException(HttpStatus.BAD_REQUEST, ex.message ?: "bad request")
        }
        val merchantId = target.merchantId ?: orderSummary.merchantId
        val branchId = credentialResolver.resolveBranch(merchantId, target.branchId ?: orderSummary.branchId, target.productId)
            ?: throw StripeCheckoutException(HttpStatus.BAD_REQUEST, "branch cannot be resolved: supply branchId or productId")
        if (merchantId != orderSummary.merchantId) {
            throw StripeCheckoutException(HttpStatus.BAD_REQUEST, "merchantId does not match the order")
        }
        return StripePaymentContext(merchantId, orderId, orderSummary, branchId)
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
}
