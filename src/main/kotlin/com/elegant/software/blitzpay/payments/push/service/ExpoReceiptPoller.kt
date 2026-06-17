package com.elegant.software.blitzpay.payments.push.service

import com.elegant.software.blitzpay.payments.push.config.ExpoPushProperties
import com.elegant.software.blitzpay.payments.push.persistence.model.DeliveryOutcome
import com.elegant.software.blitzpay.payments.push.persistence.model.PushDeliveryAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.Instant

@Component
 class ExpoReceiptPoller(
    private val properties: ExpoPushProperties,
    private val expoWebClient: WebClient,
    private val attemptRepository: PushDeliveryAttemptRepository,
    private val deviceRegistrationService: DeviceRegistrationService,
) {
    private val log = LoggerFactory.getLogger(ExpoReceiptPoller::class.java)

    @Scheduled(fixedDelayString = "\${blitzpay.expo.receipt-poll-interval-ms:60000}")
    open fun poll() {
        val cutoff = Instant.now().minus(Duration.ofMinutes(properties.receiptDelayMinutes))
        val pending = attemptRepository.findAll()
            .filter { it.ticketId != null && it.receiptOutcome == null && it.createdAt.isBefore(cutoff) }
            .take(1000)
        if (pending.isEmpty()) return

        val ticketIds = pending.mapNotNull { it.ticketId }
        val receipts = try {
            expoWebClient.post()
                .uri(properties.receiptsUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("ids" to ticketIds))
                .retrieve()
                .bodyToMono(ReceiptsResponse::class.java)
                .block(Duration.ofMillis(properties.requestTimeoutMs))
                ?.data ?: emptyMap()
        } catch (ex: Exception) {
            log.warn("expo receipts fetch failed size={}", ticketIds.size, ex)
            return
        }

        pending.forEach { attempt ->
            val receipt = receipts[attempt.ticketId] ?: return@forEach
            val errorCode = receipt.details?.error
            if (receipt.status == "ok") {
                attempt.receiptOutcome = DeliveryOutcome.ACCEPTED
            } else {
                attempt.receiptOutcome = DeliveryOutcome.REJECTED
                attempt.errorCode = errorCode ?: attempt.errorCode
                if (errorCode == "DeviceNotRegistered" || errorCode == "InvalidCredentials") {
                    deviceRegistrationService.markInvalid(attempt.expoPushToken)
                }
            }
            attempt.updatedAt = Instant.now()
            attemptRepository.save(attempt)
        }
    }

    data class ReceiptsResponse(val data: Map<String, Receipt>? = null)
    data class Receipt(val status: String? = null, val message: String? = null, val details: Details? = null)
    data class Details(val error: String? = null)
}
