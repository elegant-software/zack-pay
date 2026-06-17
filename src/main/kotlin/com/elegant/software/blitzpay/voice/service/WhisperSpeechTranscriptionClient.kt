package com.elegant.software.blitzpay.voice.service

import com.elegant.software.blitzpay.voice.config.VoiceProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@Component
class WhisperSpeechTranscriptionClient(
    @Qualifier("whisperWebClient")
    private val whisperWebClient: WebClient,
    private val properties: VoiceProperties,
) : SpeechTranscriptionClient {

    override fun transcribe(submission: VoiceAudioSubmission): VoiceTranscription {
        val body = MultipartBodyBuilder().apply {
            part("model", properties.whisper.model)
            part("response_format", "verbose_json")
            part("file", object : ByteArrayResource(submission.bytes) {
                override fun getFilename(): String = submission.filename ?: "voice-input"
            }).contentType(MediaType.parseMediaType(submission.contentType))
        }.build()

        val response = runCatching {
            whisperWebClient.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WhisperTranscriptionResponse::class.java)
                .block()
        }.getOrElse { throw UpstreamAiException("Voice transcription is temporarily unavailable.", it) }

        val transcript = response?.text?.trim().orEmpty()
        if (transcript.isBlank()) {
            throw NoSpeechDetectedException()
        }

        return VoiceTranscription(
            text = transcript,
            language = response?.language,
            durationSeconds = response?.duration?.let { BigDecimal.valueOf(it) },
        )
    }

    private data class WhisperTranscriptionResponse(
        val text: String? = null,
        val language: String? = null,
        val duration: Double? = null,
    )
}
