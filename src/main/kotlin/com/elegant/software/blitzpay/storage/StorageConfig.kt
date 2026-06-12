package com.elegant.software.blitzpay.storage

import com.elegant.software.blitzpay.config.ObservabilitySupport
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfig {

    @Bean
    fun s3Client(props: StorageProperties): S3Client =
        S3Client.builder()
            .endpointOverride(URI.create(props.endpoint))
            .region(Region.of(props.region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey, props.secretKey)
            ))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(props.pathStyleAccess)
                    .build()
            )
            .build()

    @Bean
    fun s3Presigner(props: StorageProperties): S3Presigner =
        S3Presigner.builder()
            .endpointOverride(URI.create(props.endpoint))
            .region(Region.of(props.region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey, props.secretKey)
            ))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(props.pathStyleAccess)
                    .build()
            )
            .build()

    @Bean
    fun storageService(
        s3Client: S3Client,
        s3Presigner: S3Presigner,
        props: StorageProperties,
        observability: ObservabilitySupport,
    ): StorageService = S3StorageService(s3Client, s3Presigner, props, observability)
}
