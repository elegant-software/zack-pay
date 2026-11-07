package com.elegant.software.blitzpay.payments.push.web

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusResponse
import com.elegant.software.blitzpay.payments.push.service.PaymentStatusService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Payment Status", description = "Fallback query for authoritative payment status")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments", version = "1")
class PaymentStatusController(
    private val paymentStatusService: PaymentStatusService,
) {
    @Operation(summary = "Fetch the authoritative current status of a payment request.")
    @GetMapping("/{paymentRequestId}")
    fun getPaymentStatus(@PathVariable paymentRequestId: String): ResponseEntity<PaymentStatusResponse> {
        val id = try {
            UUID.fromString(paymentRequestId)
        } catch (_: IllegalArgumentException) {
            throw MalformedPaymentRequestIdException(paymentRequestId)
        }
        return paymentStatusService.getByRequestId(id)
            .map { ResponseEntity.ok(it) }
            .orElseGet { ResponseEntity.status(HttpStatus.NOT_FOUND).build() }
    }
}

class MalformedPaymentRequestIdException(val value: String) : ApiException(HttpStatus.BAD_REQUEST, "Malformed paymentRequestId")