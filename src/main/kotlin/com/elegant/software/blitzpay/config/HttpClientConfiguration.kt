package com.elegant.software.blitzpay.config

import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class HttpClientConfiguration {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    fun webClientBuilder(observationRegistry: ObservationRegistry): WebClient.Builder =
        WebClient.builder().observationRegistry(observationRegistry)

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    fun restClientBuilder(observationRegistry: ObservationRegistry): RestClient.Builder =
        RestClient.builder().observationRegistry(observationRegistry)
}
