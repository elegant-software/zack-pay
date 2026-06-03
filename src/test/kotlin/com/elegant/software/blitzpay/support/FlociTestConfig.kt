package com.elegant.software.blitzpay.support

import io.floci.testcontainers.FlociContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.junit.jupiter.Container

@TestConfiguration(proxyBeanMethods = false)
class FlociTestConfig {

    @Bean
    @ServiceConnection
    fun floci(): FlociContainer = FlociContainer()
}
