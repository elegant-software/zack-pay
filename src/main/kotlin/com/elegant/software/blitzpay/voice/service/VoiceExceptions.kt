package com.elegant.software.blitzpay.voice.service

open class VoiceException(
    val reason: String,
    override val message: String,
    val statusCode: Int,
) : RuntimeException(message)

class MissingAuthorizationException : VoiceException(
    reason = "MISSING_AUTHORIZATION",
    message = "Authenticated access is required.",
    statusCode = 401,
)

class MissingAudioException : VoiceException(
    reason = "MISSING_AUDIO",
    message = "Multipart field 'audio' is required.",
    statusCode = 400,
)

class UnsupportedAudioFormatException(contentType: String) : VoiceException(
    reason = "UNSUPPORTED_AUDIO_FORMAT",
    message = "Unsupported audio format: $contentType",
    statusCode = 415,
)

class PayloadTooLargeException(maxUploadBytes: Long) : VoiceException(
    reason = "PAYLOAD_TOO_LARGE",
    message = "Audio file exceeds the configured upload limit of $maxUploadBytes bytes.",
    statusCode = 413,
)

class AudioTooShortException : VoiceException(
    reason = "AUDIO_TOO_SHORT",
    message = "Recording must be at least 1 second long.",
    statusCode = 400,
)

class AudioTooLongException(maxDurationSeconds: Long) : VoiceException(
    reason = "AUDIO_TOO_LONG",
    message = "Recording exceeds the maximum allowed duration of $maxDurationSeconds seconds.",
    statusCode = 400,
)

class NoSpeechDetectedException : VoiceException(
    reason = "NO_SPEECH_DETECTED",
    message = "No recognizable speech was detected in the uploaded recording.",
    statusCode = 400,
)

class UpstreamAiException(message: String, cause: Throwable? = null) : VoiceException(
    reason = "UPSTREAM_AI_ERROR",
    message = message,
    statusCode = 502,
) {
    init {
        if (cause != null) initCause(cause)
    }
}
