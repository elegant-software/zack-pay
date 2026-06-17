package com.elegant.software.blitzpay.payments.qrpay.web

import com.elegant.software.blitzpay.payments.support.PaymentUpdateBus
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/qr-payments", version = "1")
class QrPaymentSseController(private val bus: PaymentUpdateBus) {
    private val logger = LoggerFactory.getLogger(QrPaymentSseController::class.java)

    @GetMapping(value = ["/{paymentRequestId}/events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@PathVariable paymentRequestId: UUID): SseEmitter {
        logger.info("SSE client connected for paymentRequestId: {}", paymentRequestId)
        val emitter = SseEmitter(300_000L) // 5-minute timeout
        bus.register(paymentRequestId, emitter)
        return emitter
    }
}
