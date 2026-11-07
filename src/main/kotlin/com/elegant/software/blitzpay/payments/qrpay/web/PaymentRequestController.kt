package com.elegant.software.blitzpay.payments.qrpay.web

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.support.PaymentUpdateBus
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentGateway
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RestController
import java.util.Base64
import java.util.*

@ConditionalOnProperty(name = ["truelayer.enabled"], havingValue = "true")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments", version = "1")

class PaymentRequestController(
    private val orderGateway: OrderGateway,
    private val paymentGateway: PaymentGateway,
    private val paymentUpdateBus: PaymentUpdateBus,
    private val paymentStatusInitializationGateway: PaymentStatusInitializationGateway,
) {
    private val log = LoggerFactory.getLogger(PaymentRequestController::class.java)

    @PostMapping("/request")
    fun createPaymentRequest(
        @RequestBody request: PaymentRequested,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<Any> {
        log.info(
            "truelayer payment request received orderId={} amountMinorUnits={} currency={} userEmailPresent={} redirectReturnUri={}",
            request.orderId, request.amountMinorUnits, request.currency,
            !request.userEmail.isNullOrBlank(), request.redirectReturnUri,
        )
        val orderSummary = try {
            orderGateway.assertPayable(request.orderId)
        } catch (ex: NoSuchElementException) {
            log.warn("truelayer payment rejected reason=order_not_found orderId={}", request.orderId)
            throw ApiException(HttpStatus.NOT_FOUND, (ex.message ?: "Order not found"))
        } catch (ex: IllegalStateException) {
            log.warn("truelayer payment rejected reason=order_not_payable orderId={} error={}", request.orderId, ex.message)
            throw ApiException(HttpStatus.CONFLICT, (ex.message ?: "Order is not payable"))
        }
        if (request.amountMinorUnits != orderSummary.totalAmountMinor || !request.currency.equals(orderSummary.currency, ignoreCase = true)) {
            log.warn(
                "truelayer payment rejected reason=amount_mismatch orderId={} requestedAmount={} requestedCurrency={} orderAmount={} orderCurrency={}",
                request.orderId, request.amountMinorUnits, request.currency,
                orderSummary.totalAmountMinor, orderSummary.currency,
            )
            throw ApiException(HttpStatus.BAD_REQUEST, "amountMinorUnits and currency must match the order")
        }
        if (request.userEmail.isNullOrBlank()) {
            log.warn("truelayer payment rejected reason=missing_user_email orderId={}", request.orderId)
            throw ApiException(HttpStatus.BAD_REQUEST, "userEmail is required for TrueLayer payments")
        }

        val paymentRequestId = UUID.randomUUID()
        request.paymentRequestId = paymentRequestId
        request.payerRef = extractSubject(authorization)
        return LogContext.with(
            LogContext.ORDER_ID to request.orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "TRUELAYER",
        ) {
            log.info(
                "truelayer payment request amountMinorUnits={} currency={} payerRefPresent={} userEmailPresent={} redirectReturnUri={}",
                request.amountMinorUnits, request.currency, !request.payerRef.isNullOrBlank(),
                !request.userEmail.isNullOrBlank(), request.redirectReturnUri,
            )
            paymentStatusInitializationGateway.initialize(
                paymentRequestId = paymentRequestId,
                payerRef = request.payerRef,
                orderId = request.orderId,
                amountMinorUnits = request.amountMinorUnits,
                currency = request.currency,
            )
            log.info("truelayer payment status initialized")
            orderGateway.linkPaymentAttempt(request.orderId, paymentRequestId, "TRUELAYER", null)
            log.info("truelayer payment attempt linked to order")

            val result = paymentGateway.startPayment(request)
            log.info(
                "truelayer payment gateway started paymentId={} resourceTokenPresent={} redirectReturnUri={}",
                result.paymentId, !result.resourceToken.isNullOrBlank(), result.redirectReturnUri ?: request.redirectReturnUri,
            )
            result.paymentId?.let {
                orderGateway.linkPaymentAttempt(request.orderId, paymentRequestId, "TRUELAYER", it)
                log.info("truelayer payment attempt linked providerReference={}", it)
            }
            paymentUpdateBus.emit(paymentRequestId, result)
            log.info("truelayer payment update emitted to bus")

            ResponseEntity.accepted().body(
                mapOf(
                    "paymentRequestId" to paymentRequestId.toString(),
                    "paymentId" to result.paymentId,
                    "resourceToken" to result.resourceToken,
                    "redirectReturnUri" to (result.redirectReturnUri ?: request.redirectReturnUri)
                )
            )
        }
    }

    private fun extractSubject(authorization: String?): String? {
        val bearerToken = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parts = bearerToken.split('.')
        if (parts.size < 2) return null

        return runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.mapCatching { payload ->
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(512)
    }
}
