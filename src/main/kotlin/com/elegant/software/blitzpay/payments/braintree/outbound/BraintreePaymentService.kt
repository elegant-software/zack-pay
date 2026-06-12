package com.elegant.software.blitzpay.payments.braintree.internal

import com.braintreegateway.ClientTokenRequest
import com.braintreegateway.TransactionRequest
import com.elegant.software.blitzpay.config.ObservabilitySupport
import com.elegant.software.blitzpay.merchant.api.BraintreeCredentials
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID

sealed class BraintreeCheckoutResult {
    data class Success(
        val transactionId: String,
        val amount: String,
        val currency: String,
        val orderId: String,
        val invoiceId: String? = null,
        val merchantId: UUID,
        val branchId: UUID?,
    ) : BraintreeCheckoutResult()

    data class Failure(val message: String, val code: String?) : BraintreeCheckoutResult()
}

@Service
class BraintreePaymentService(
    private val gatewayFactory: BraintreeGatewayFactory,
    private val observability: ObservabilitySupport,
) {

    private val log = LoggerFactory.getLogger(BraintreePaymentService::class.java)

    fun generateClientToken(credentials: BraintreeCredentials): String {
        val token = observe("client-token") {
            gatewayFactory.get(credentials).clientToken().generate(ClientTokenRequest())
        }
        log.info("braintree client_token issued merchantId={}", credentials.merchantId)
        return token
    }

    fun checkout(
        nonce: String,
        amount: Double,
        currency: String,
        credentials: BraintreeCredentials,
        merchantId: UUID,
        branchId: UUID?,
        orderId: String,
        productId: UUID? = null,
        invoiceId: String? = null,
    ): BraintreeCheckoutResult {
        require(nonce.isNotBlank()) { "nonce is required" }
        require(amount > 0 && amount.isFinite()) { "amount must be a positive number" }
        val formattedAmount = "%.2f".format(Locale.US, amount)
        val request = TransactionRequest()
            .amount(BigDecimal(formattedAmount))
            .paymentMethodNonce(nonce)
            .orderId(orderId)
            .options().submitForSettlement(true).done()
        val result = observe("checkout", orderId) {
            gatewayFactory.get(credentials).transaction().sale(request)
        }
        return if (result.isSuccess && result.target != null) {
            val tx = result.target
            log.info(
                "braintree checkout OK tx={} amount={} currency={} merchantId={} branchId={} orderId={} productId={} invoice={}",
                tx.id, formattedAmount, currency, merchantId, branchId, orderId, productId, invoiceId ?: "n/a",
            )
            BraintreeCheckoutResult.Success(tx.id, formattedAmount, currency, orderId, invoiceId, merchantId, branchId)
        } else {
            val tx = result.transaction
            val msg = tx?.processorResponseText ?: result.message ?: "Braintree declined the transaction"
            val code = tx?.processorResponseCode
            log.warn(
                "braintree checkout FAILED code={} message={} merchantId={} branchId={} orderId={} invoice={}",
                code ?: "n/a", msg, merchantId, branchId, orderId, invoiceId ?: "n/a",
            )
            BraintreeCheckoutResult.Failure(msg, code)
        }
    }

    private fun <T> observe(operation: String, orderReference: String? = null, block: () -> T): T =
        observability.observe(
            name = "blitzpay.external",
            contextualName = "braintree $operation",
            lowCardinality = mapOf("provider" to "braintree", "operation" to operation),
            highCardinality = mapOf("order.reference" to orderReference),
            block = block,
        )
}
