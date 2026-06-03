package com.elegant.software.blitzpay.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "blitzpay.storage")
data class StorageProperties(val bucket: String)
