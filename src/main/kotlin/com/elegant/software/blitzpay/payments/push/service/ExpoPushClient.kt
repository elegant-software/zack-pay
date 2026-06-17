package com.elegant.software.blitzpay.payments.push.service

import com.elegant.software.blitzpay.payments.push.config.ExpoPushProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import java.time.Duration

data class ExpoMessage(
    val to: String,
    val title: String,
    val body: String,
    val data: Map<String, Any?> = emptyMap(),
)

data class ExpoTicket(
    val token: String,
    val ticketId: String?,
    val status: Status,
    val errorCode: String?,
) {
    enum class Status { OK, ERROR }
}

@Component
open class ExpoPushClient(
    private val properties: ExpoPushProperties,
    private val expoWebClient: WebClient,
) {
    private val log = LoggerFactory.getLogger(ExpoPushClient::class.java)

    open fun send(messages: List<ExpoMessage>): List<ExpoTicket> {
        if (messages.isEmpty()) return emptyList()
        return messages.chunked(properties.maxBatchSize).flatMap { batch ->
            sendBatch(batch)
        }
    }

    private fun sendBatch(batch: List<ExpoMessage>): List<ExpoTicket> = try {
        val response = expoWebClient.post()
            .uri(properties.baseUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .retrieve()
            .bodyToMono(ExpoPushResponse::class.java)
            .retryWhen(
                Retry.backoff(3, Duration.ofMillis(200))
                    .maxBackoff(Duration.ofSeconds(2))
                    .filter { it !is IllegalArgumentException }
            )
            .block(Duration.ofMillis(properties.requestTimeoutMs))

        val data = response?.data ?: emptyList()
        batch.mapIndexed { index, message ->
            val item = data.getOrNull(index)
            if (item?.status == "ok") {
                ExpoTicket(message.to, item.id, ExpoTicket.Status.OK, null)
            } else {
                ExpoTicket(message.to, item?.id, ExpoTicket.Status.ERROR, item?.details?.error)
            }
        }
    } catch (ex: Exception) {
        log.error("expo push batch failed size={}", batch.size, ex)
        batch.map { ExpoTicket(it.to, null, ExpoTicket.Status.ERROR, "transport_error") }
    }

    data class ExpoPushResponse(val data: List<Item>? = null) {
        data class Item(
            val id: String? = null,
            val status: String? = null,
            val message: String? = null,
            val details: Details? = null,
        )
        data class Details(val error: String? = null)
    }
}
