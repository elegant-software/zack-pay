package com.elegant.software.blitzpay.storage

import com.elegant.software.blitzpay.config.ObservabilitySupport
import io.awspring.cloud.autoconfigure.s3.S3ClientCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfig {

    // ponytail: S3Client and S3Presigner are auto-configured by Spring Cloud AWS

    // Applied after the shared endpoint property, and only to S3Client: S3Presigner ignores
    // S3ClientCustomizers, so presigned URLs still carry the public host.
    @Bean
    fun internalEndpointS3ClientCustomizer(props: StorageProperties) = S3ClientCustomizer { builder ->
        props.internalEndpoint?.takeIf { it.isNotBlank() }?.let { builder.endpointOverride(URI.create(it)) }
    }

    @Bean
    fun storageService(
        s3Client: S3Client,
        s3Presigner: S3Presigner,
        props: StorageProperties,
        observability: ObservabilitySupport,
    ): StorageService = S3StorageService(s3Client, s3Presigner, props, observability)
}
