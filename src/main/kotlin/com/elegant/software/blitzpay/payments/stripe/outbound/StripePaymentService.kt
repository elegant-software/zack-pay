package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.config.ObservabilitySupport
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.exception.StripeException
import com.stripe.model.PaymentIntent
import com.stripe.model.checkout.Session
import com.stripe.net.RequestOptions
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.PaymentIntentCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

data class StripeIntentResult(
    val clientSecret: String,
    val paymentIntentId: String,
    val connectedAccountId: String,
)

data class StripeCheckoutSessionResult(
    val checkoutUrl: String,
    val checkoutSessionId: String,
    val connectedAccountId: String,
)

@Service
class StripePaymentService(
    private val stripeProperties: StripeProperties,
    private val observability: ObservabilitySupport,
) {
    private val log = LoggerFactory.getLogger(StripePaymentService::class.java)

    fun createIntent(
        amount: Double,
        currency: String,
        connectedAccountId: String,
        merchantId: UUID,
        branchId: UUID?,
        orderId: String,
        paymentRequestId: UUID,
        productId: UUID?,
        paymentMethodType: String = "card",
    ): StripeIntentResult {
        require(amount > 0 && amount.isFinite()) { "amount must be a positive number" }
        val amountInSmallestUnit = Math.round(amount * 100)
        val paramsBuilder = PaymentIntentCreateParams.builder()
            .setAmount(amountInSmallestUnit)
            .setCurrency(currency.lowercase())
            .apply {
                if (paymentMethodType == "card_present") {
                    addPaymentMethodType("card_present")
                    setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                } else {
                    setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                    )
                }
            }
            .putMetadata("merchantId", merchantId.toString())
            .putMetadata("orderId", orderId)
            .putMetadata("paymentRequestId", paymentRequestId.toString())
            .apply { branchId?.let { putMetadata("branchId", it.toString()) } }
            .apply { productId?.let { putMetadata("productId", it.toString()) } }
        if (stripeProperties.applicationFeeAmount > 0) {
            paramsBuilder.setApplicationFeeAmount(stripeProperties.applicationFeeAmount)
        }
        val requestOptions = RequestOptions.builder()
            .setApiKey(stripeProperties.secretKey)
            .setStripeAccount(connectedAccountId)
            .build()
        try {
            val intent = observe("create-intent", orderId, paymentRequestId) {
                PaymentIntent.create(paramsBuilder.build(), requestOptions)
            }
            log.info(
                "stripe create_intent id={} amount={} currency={} merchantId={} connectedAccountId={} branchId={} orderId={} paymentRequestId={} productId={}",
                intent.id, amount, currency.lowercase(), merchantId, connectedAccountId, branchId, orderId, paymentRequestId, productId,
            )
            return StripeIntentResult(
                clientSecret = requireNotNull(intent.clientSecret) { "Stripe returned null clientSecret" },
                paymentIntentId = intent.id,
                connectedAccountId = connectedAccountId,
            )
        } catch (ex: StripeException) {
            log.error(
                "stripe create_intent FAILED amount={} currency={} merchantId={} connectedAccountId={} code={} message={}",
                amount, currency, merchantId, connectedAccountId, ex.code, ex.message,
            )
            throw ex
        }
    }

    fun createPayByBankCheckoutSession(
        amount: Double,
        currency: String,
        connectedAccountId: String,
        merchantId: UUID,
        branchId: UUID?,
        orderId: String,
        paymentRequestId: UUID,
        productId: UUID?,
        successUrl: String,
        cancelUrl: String,
        statementDescriptor: String,
    ): StripeCheckoutSessionResult {
        require(amount > 0 && amount.isFinite()) { "amount must be a positive number" }
        require(successUrl.isNotBlank()) { "successUrl must not be blank" }
        require(cancelUrl.isNotBlank()) { "cancelUrl must not be blank" }
        val amountInSmallestUnit = Math.round(amount * 100)
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.PAY_BY_BANK)
            .setPaymentIntentData(
                SessionCreateParams.PaymentIntentData.builder()
                    .apply {
                        if (stripeProperties.applicationFeeAmount > 0) {
                            setApplicationFeeAmount(stripeProperties.applicationFeeAmount)
                        }
                    }
                    .setStatementDescriptor(statementDescriptor)
                    .putMetadata("merchantId", merchantId.toString())
                    .putMetadata("orderId", orderId)
                    .putMetadata("paymentRequestId", paymentRequestId.toString())
                    .apply { branchId?.let { putMetadata("branchId", it.toString()) } }
                    .apply { productId?.let { putMetadata("productId", it.toString()) } }
                    .build()
            )
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency.lowercase())
                            .setUnitAmount(amountInSmallestUnit)
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("Order $orderId")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .putMetadata("merchantId", merchantId.toString())
            .putMetadata("orderId", orderId)
            .putMetadata("paymentRequestId", paymentRequestId.toString())
            .build()
        val requestOptions = RequestOptions.builder()
            .setApiKey(stripeProperties.secretKey)
            .setStripeAccount(connectedAccountId)
            .build()
        try {
            val session = observe("create-checkout-session", orderId, paymentRequestId) {
                Session.create(params, requestOptions)
            }
            log.info(
                "stripe pay_by_bank checkout_session id={} amount={} currency={} merchantId={} connectedAccountId={} branchId={} orderId={} paymentRequestId={} productId={}",
                session.id, amount, currency.lowercase(), merchantId, connectedAccountId, branchId, orderId, paymentRequestId, productId,
            )
            return StripeCheckoutSessionResult(
                checkoutUrl = requireNotNull(session.url) { "Stripe returned null checkout session url" },
                checkoutSessionId = session.id,
                connectedAccountId = connectedAccountId,
            )
        } catch (ex: StripeException) {
            log.error(
                "stripe pay_by_bank checkout_session FAILED amount={} currency={} merchantId={} connectedAccountId={} code={} message={}",
                amount, currency, merchantId, connectedAccountId, ex.code, ex.message,
            )
            throw ex
        }
    }

    private fun <T> observe(
        operation: String,
        orderReference: String,
        paymentRequestReference: UUID,
        block: () -> T,
    ): T = observability.observe(
        name = "blitzpay.external",
        contextualName = "stripe $operation",
        lowCardinality = mapOf("provider" to "stripe", "operation" to operation),
        highCardinality = mapOf(
            "order.reference" to orderReference,
            "payment.request.reference" to paymentRequestReference.toString(),
        ),
        block = block,
    )
}
