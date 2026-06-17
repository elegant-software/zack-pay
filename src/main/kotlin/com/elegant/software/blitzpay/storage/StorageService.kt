package com.elegant.software.blitzpay.storage

import java.time.Instant

interface StorageService {
    fun upload(storageKey: String, contentType: String, bytes: ByteArray)
    fun download(storageKey: String): ByteArray
    fun presignUpload(storageKey: String, contentType: String, ttlMinutes: Long = 15): PresignedUpload
    fun presignDownload(storageKey: String, ttlMinutes: Long = 60): String
    fun delete(storageKey: String)
}

data class PresignedUpload(
    val storageKey: String,
    val uploadUrl: String,
    val expiresAt: Instant
)
