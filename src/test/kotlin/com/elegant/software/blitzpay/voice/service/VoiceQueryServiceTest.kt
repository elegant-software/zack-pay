package com.elegant.software.blitzpay.voice.service

import com.elegant.software.blitzpay.voice.config.VoiceProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import kotlin.test.assertEquals

class VoiceQueryServiceTest {
    private val transcriptionClient = mock<SpeechTranscriptionClient>()
    private val properties = VoiceProperties()
    private val service = VoiceQueryService(
        transcriptionClient = transcriptionClient,
        properties = properties,
    )

    private val submission = VoiceAudioSubmission(
        bytes = "audio".toByteArray(),
        contentType = "audio/mp4",
        callerSubject = "user-123",
    )

    @Test
    fun `process returns transcript and language from whisper`() {
        whenever(transcriptionClient.transcribe(submission)).thenReturn(
            VoiceTranscription(
                text = "What is my latest payment?",
                language = "en",
                durationSeconds = BigDecimal("2.5"),
            )
        )

        val result = service.process(submission)

        assertEquals("What is my latest payment?", result.transcript)
        assertEquals("en", result.language)
    }

    @Test
    fun `process rejects too short audio`() {
        whenever(transcriptionClient.transcribe(submission)).thenReturn(
            VoiceTranscription(text = "payment", durationSeconds = BigDecimal("0.5"))
        )

        assertThrows<AudioTooShortException> { service.process(submission) }
    }

    @Test
    fun `process rejects too long audio`() {
        whenever(transcriptionClient.transcribe(submission)).thenReturn(
            VoiceTranscription(text = "payment", durationSeconds = BigDecimal("61"))
        )

        assertThrows<AudioTooLongException> { service.process(submission) }
    }

    @Test
    fun `process propagates upstream transcription failure`() {
        whenever(transcriptionClient.transcribe(submission)).thenThrow(
            UpstreamAiException("Voice transcription is temporarily unavailable.")
        )

        assertThrows<UpstreamAiException> { service.process(submission) }
    }
}
