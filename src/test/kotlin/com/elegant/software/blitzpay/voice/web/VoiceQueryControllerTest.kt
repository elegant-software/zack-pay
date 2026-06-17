package com.elegant.software.blitzpay.voice.web

import com.elegant.software.blitzpay.voice.api.VoiceGateway
import com.elegant.software.blitzpay.voice.config.VoiceProperties
import com.elegant.software.blitzpay.voice.service.MissingAuthorizationException
import com.elegant.software.blitzpay.voice.service.PayloadTooLargeException
import com.elegant.software.blitzpay.voice.service.UnsupportedAudioFormatException
import com.elegant.software.blitzpay.voice.service.VoiceTranscriptionResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import kotlin.test.assertEquals

class VoiceQueryControllerTest {
    private val voiceGateway = mock<VoiceGateway>()
    private val controller = VoiceQueryController(
        voiceGateway = voiceGateway,
        properties = VoiceProperties(maxUploadBytes = 8),
    )

    @Test
    fun `returns transcript JSON for valid authenticated multipart request`() {
        whenever(voiceGateway.process(any())).thenReturn(
            VoiceTranscriptionResponse(
                transcript = "What is my latest payment?",
                language = "en",
            )
        )

        val response = controller.query(
            audio = multipartFile("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3, 4)),
            authorization = bearerToken("user-123"),
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        assertEquals("What is my latest payment?", response.body?.transcript)
        assertEquals("en", response.body?.language)
    }

    @Test
    fun `throws unauthorized when authorization header missing`() {
        val exception = assertThrows<MissingAuthorizationException> {
            controller.query(
                audio = multipartFile("audio.mp4", "audio/mp4", byteArrayOf(1, 2)),
                authorization = null,
            )
        }

        assertEquals("MISSING_AUTHORIZATION", exception.reason)
    }

    @Test
    fun `throws unsupported media type for invalid audio content type`() {
        val exception = assertThrows<UnsupportedAudioFormatException> {
            controller.query(
                audio = multipartFile("audio.txt", "text/plain", "bad".toByteArray()),
                authorization = bearerToken("user-123"),
            )
        }

        assertEquals("UNSUPPORTED_AUDIO_FORMAT", exception.reason)
    }

    @Test
    fun `throws payload too large when upload exceeds configured limit`() {
        val exception = assertThrows<PayloadTooLargeException> {
            controller.query(
                audio = multipartFile("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)),
                authorization = bearerToken("user-123"),
            )
        }

        assertEquals("PAYLOAD_TOO_LARGE", exception.reason)
    }

    private fun multipartFile(filename: String, contentType: String, bytes: ByteArray): MockMultipartFile =
        MockMultipartFile("audio", filename, contentType, bytes)

    private fun bearerToken(subject: String): String {
        val header = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"$subject"}""".toByteArray())
        return "Bearer $header.$payload.signature"
    }
}
