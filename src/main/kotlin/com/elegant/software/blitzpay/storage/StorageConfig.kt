package com.elegant.software.blitzpay.storage

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfig {

    @Bean
    fun storageService(s3Client: S3Client, s3Presigner: S3Presigner, props: StorageProperties): StorageService =
        S3StorageService(s3Client, s3Presigner, props)
}
