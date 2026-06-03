package com.elegant.software.blitzpay.payments.truelayer.outbound

import com.elegant.software.blitzpay.config.ObservabilitySupport
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentGateway
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import com.elegant.software.blitzpay.payments.truelayer.config.TrueLayerProperties
import com.truelayer.java.TrueLayerClient
import com.truelayer.java.entities.CurrencyCode
import com.truelayer.java.entities.User
import com.truelayer.java.http.entities.ApiResponse
import com.truelayer.java.payments.entities.*
import com.truelayer.java.payments.entities.beneficiary.MerchantAccount
import com.truelayer.java.payments.entities.paymentmethod.PaymentMethod
import com.truelayer.java.payments.entities.providerselection.ProviderSelection
import com.truelayer.java.payments.entities.schemeselection.preselected.SchemeSelection
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * TrueLayer payment gateway adapter.
 *
 * For now, we DO NOT perform any outbound API call. Instead, we:
 *  - Build a request-shaped object equivalent to TrueLayer's CreatePaymentRequest
 *  - Log it for traceability
 *  - Return a deterministic placeholder payment id
 *
 * This can be swapped later with the official TrueLayer Java SDK call, using the
 * same fields assembled below.
 */
@EnableConfigurationProperties(TrueLayerProperties::class)
@ConditionalOnProperty(name = ["truelayer.enabled"], havingValue = "true", matchIfMissing = true)
@Component
class PaymentService(
    private val trueLayerProperties: TrueLayerProperties,
    private val trueLayerClient: TrueLayerClient,
    private val observability: ObservabilitySupport,
) : PaymentGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun startPayment(paymentRequest: PaymentRequested): PaymentResult {
        // Adapt types for SDK requirements
        val amountInMinor = paymentRequest.amountMinorUnits.toInt() // SDK likely expects Int
        val currency = CurrencyCode.valueOf(paymentRequest.currency.uppercase()) // Use SDK's CurrencyCode enum
        val createTrueLayerPaymentRequest = CreatePaymentRequest.builder()
            .amountInMinor(amountInMinor)
            .currency(currency)
            .paymentMethod(
                PaymentMethod.bankTransfer()
                    .providerSelection(
                        ProviderSelection.preselected()
                            .providerId(trueLayerProperties.mockProviderId)
                            .schemeSelection(SchemeSelection.preselected().schemeId(trueLayerProperties.mockSchemeId).build())
                            .build()
                    )
                    .beneficiary(
                        MerchantAccount.builder()
                            .merchantAccountId(trueLayerProperties.merchantAccountId)
                            .build()
                    )

                    .build()
            )
            .user(
                User.builder()
                    .name(paymentRequest.userDisplayName)
                    .email(paymentRequest.userEmail)
                    .build()
            ).metadata(
                buildMap {
                    put("paymentRequestId", paymentRequest.paymentRequestId.toString())
                    put("orderId", paymentRequest.orderId)
                    paymentRequest.payerRef?.let { put("payerRef", it) }
                }
            )
            .build()

        log.info(
            "truelayer createPayment request paymentRequestId={} orderId={} amountMinor={} currency={} merchantAccount={} providerId={} schemeId={}",
            paymentRequest.paymentRequestId,
            paymentRequest.orderId,
            amountInMinor,
            currency,
            trueLayerProperties.merchantAccountId,
            trueLayerProperties.mockProviderId,
            trueLayerProperties.mockSchemeId,
        )

        val started = System.nanoTime()
        val apiResponse: ApiResponse<CreatePaymentResponse?>? = try {
            observability.observe(
                name = "blitzpay.external",
                contextualName = "truelayer create-payment",
                lowCardinality = mapOf("provider" to "truelayer", "operation" to "create-payment"),
                highCardinality = mapOf(
                    "order.reference" to paymentRequest.orderId,
                    "payment.request.reference" to paymentRequest.paymentRequestId?.toString(),
                ),
            ) {
                trueLayerClient.payments()
                    .createPayment(createTrueLayerPaymentRequest)
                    .get(API_CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            }
        } catch (ex: TimeoutException) {
            log.error(
                "truelayer createPayment TIMEOUT paymentRequestId={} orderId={} timeoutMs={}",
                paymentRequest.paymentRequestId, paymentRequest.orderId, API_CALL_TIMEOUT.toMillis(), ex,
            )
            return errorResult(paymentRequest, "truelayer_timeout")
        } catch (ex: ExecutionException) {
            val cause = ex.cause ?: ex
            log.error(
                "truelayer createPayment THREW paymentRequestId={} orderId={} errorClass={} error={}",
                paymentRequest.paymentRequestId, paymentRequest.orderId, cause.javaClass.simpleName, cause.message, cause,
            )
            return errorResult(paymentRequest, "truelayer_exception:${cause.javaClass.simpleName}")
        } catch (ex: Exception) {
            log.error(
                "truelayer createPayment INTERRUPTED paymentRequestId={} orderId={} errorClass={} error={}",
                paymentRequest.paymentRequestId, paymentRequest.orderId, ex.javaClass.simpleName, ex.message, ex,
            )
            return errorResult(paymentRequest, "truelayer_interrupted")
        }

        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        if (apiResponse == null) {
            log.error(
                "truelayer createPayment NULL_RESPONSE paymentRequestId={} orderId={} elapsedMs={}",
                paymentRequest.paymentRequestId, paymentRequest.orderId, elapsedMs,
            )
            return errorResult(paymentRequest, "truelayer_null_response")
        }
        if (apiResponse.error != null) {
            val err = apiResponse.error
            log.error(
                "truelayer createPayment API_ERROR paymentRequestId={} orderId={} httpStatus={} type={} title={} detail={} traceId={} fieldErrors={} elapsedMs={}",
                paymentRequest.paymentRequestId,
                paymentRequest.orderId,
                runCatching { err.status }.getOrNull(),
                runCatching { err.type }.getOrNull(),
                runCatching { err.title }.getOrNull(),
                runCatching { err.detail }.getOrNull(),
                runCatching { err.traceId }.getOrNull(),
                runCatching { err.errors?.toString() }.getOrNull(),
                elapsedMs,
            )
            return errorResult(paymentRequest, "truelayer_api_error:${runCatching { err.type }.getOrNull() ?: "unknown"}")
        }
        val paymentData = apiResponse.data
        if (paymentData == null) {
            log.error(
                "truelayer createPayment NULL_DATA paymentRequestId={} orderId={} elapsedMs={}",
                paymentRequest.paymentRequestId, paymentRequest.orderId, elapsedMs,
            )
            return errorResult(paymentRequest, "truelayer_null_data")
        }
        val paymentId = paymentData.id
        if (paymentId == null) {
            log.error(
                "truelayer createPayment NULL_PAYMENT_ID paymentRequestId={} orderId={} elapsedMs={}",
                paymentRequest.paymentRequestId, paymentRequest.orderId, elapsedMs,
            )
            return errorResult(paymentRequest, "truelayer_null_payment_id")
        }

        log.info(
            "truelayer createPayment OK paymentRequestId={} orderId={} paymentId={} elapsedMs={}",
            paymentRequest.paymentRequestId, paymentRequest.orderId, paymentId, elapsedMs,
        )

        val redirectURI = trueLayerClient.hppLinkBuilder()
            .returnUri(URI.create(paymentRequest.redirectReturnUri))
            .resourceToken(paymentData.resourceToken)
            .resourceId(paymentId)
            .build()
        return PaymentResult(
            paymentRequestId = paymentRequest.paymentRequestId!!,
            orderId = paymentRequest.orderId,
            paymentId = paymentId,
            resourceToken = paymentData.resourceToken,
            redirectReturnUri = paymentRequest.redirectReturnUri,
            redirectURI = redirectURI,
        )
    }

    private fun errorResult(paymentRequest: PaymentRequested, error: String) = PaymentResult(
        paymentRequestId = paymentRequest.paymentRequestId!!,
        orderId = paymentRequest.orderId,
        error = error,
    )

    companion object {
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(15)
    }
}
