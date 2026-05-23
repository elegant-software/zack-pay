package com.elegant.software.blitzpay.config

import org.springframework.context.annotation.Configuration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.repository.config.BootstrapMode

@Configuration
@EnableJpaRepositories(basePackages = ["com.elegant.software.blitzpay"], bootstrapMode = BootstrapMode.DEFERRED)
@EntityScan(basePackages = ["com.elegant.software.blitzpay", "org.springframework.modulith.events.jpa"])
class JpaConfig
