package com.elegant.software.blitzpay.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "blitzpay.storage")
data class StorageProperties(
    val bucket: String,
    // Server-side S3 calls go here when set (e.g. the in-cluster MinIO service);
    // presigned URLs keep using spring.cloud.aws.s3.endpoint, which clients must reach.
    val internalEndpoint: String? = null,
)
