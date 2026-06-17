package com.elegant.software.blitzpay.voice.service

import java.math.BigDecimal

data class VoiceAudioSubmission(
    val bytes: ByteArray,
    val contentType: String,
    val filename: String? = null,
    val sizeBytes: Long = bytes.size.toLong(),
    val callerSubject: String,
)

data class VoiceTranscription(
    val text: String,
    val language: String? = null,
    val durationSeconds: BigDecimal? = null,
)

data class VoiceTranscriptionResponse(
    val transcript: String,
    val language: String? = null,
)
