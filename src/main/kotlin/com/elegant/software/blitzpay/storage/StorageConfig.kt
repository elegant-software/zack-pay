package com.elegant.software.blitzpay.storage

import com.elegant.software.blitzpay.config.ObservabilitySupport
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfig {

    // ponytail: S3Client and S3Presigner are auto-configured by Spring Cloud AWS

    @Bean
    fun storageService(
        s3Client: S3Client,
        s3Presigner: S3Presigner,
        props: StorageProperties,
        observability: ObservabilitySupport,
    ): StorageService = S3StorageService(s3Client, s3Presigner, props, observability)
}
