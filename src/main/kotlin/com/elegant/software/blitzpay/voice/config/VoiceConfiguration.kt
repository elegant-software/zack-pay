package com.elegant.software.blitzpay.voice.config

import com.elegant.software.blitzpay.config.propagateRequestId
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

@Configuration
@EnableConfigurationProperties(VoiceProperties::class)
class VoiceConfiguration {

    @Bean
    fun whisperWebClient(properties: VoiceProperties, webClientBuilder: WebClient.Builder): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.requestTimeoutMs.toInt())
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(properties.requestTimeoutMs, TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(properties.requestTimeoutMs, TimeUnit.MILLISECONDS))
            }

        val builder = webClientBuilder
            .propagateRequestId()
            .baseUrl(properties.whisper.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")

        if (properties.whisper.apiKey.isNotBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.whisper.apiKey}")
        }

        return builder.build()
    }
}
