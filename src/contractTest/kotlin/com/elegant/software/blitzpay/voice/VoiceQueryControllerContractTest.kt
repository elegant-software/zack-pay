package com.elegant.software.blitzpay.voice

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.voice.service.MissingAudioException
import com.elegant.software.blitzpay.voice.service.NoSpeechDetectedException
import com.elegant.software.blitzpay.voice.service.UpstreamAiException
import com.elegant.software.blitzpay.voice.service.VoiceTranscriptionResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder

class VoiceQueryControllerContractTest : ContractVerifierBase() {

    @Test
    fun `returns 200 with JSON transcript for valid authenticated request`() {
        whenever(voiceGateway.process(any())).thenReturn(
            VoiceTranscriptionResponse(
                transcript = "What is my latest payment?",
                language = "en",
            )
        )

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipart("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3)))
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.transcript").isEqualTo("What is my latest payment?")
            .jsonPath("$.language").isEqualTo("en")
    }

    @Test
    fun `returns 401 when bearer token missing`() {
        webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
            .post().uri("/v1/voice/query")
            .bodyValue(multipart("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3)))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `returns 400 when audio missing`() {
        whenever(voiceGateway.process(any())).thenThrow(MissingAudioException())

        val builder = MultipartBodyBuilder()
        builder.part("unused", "value")

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(builder.build())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("MISSING_AUDIO")
    }

    @Test
    fun `returns 400 when no speech detected`() {
        whenever(voiceGateway.process(any())).thenThrow(NoSpeechDetectedException())

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipart("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3)))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("NO_SPEECH_DETECTED")
    }

    @Test
    fun `returns 502 when upstream processing fails`() {
        whenever(voiceGateway.process(any())).thenThrow(UpstreamAiException("Voice processing is temporarily unavailable."))

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipart("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3)))
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.code").isEqualTo("UPSTREAM_AI_ERROR")
    }

    private fun multipart(filename: String, contentType: String, bytes: ByteArray): org.springframework.util.MultiValueMap<String, HttpEntity<*>> {
        val builder = MultipartBodyBuilder()
        builder.part(
            "audio",
            object : ByteArrayResource(bytes) {
                override fun getFilename(): String = filename
            }
        ).contentType(MediaType.parseMediaType(contentType))
        return builder.build()
    }

    private fun bearerToken(subject: String): String {
        val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$subject"}""".toByteArray())
        return "Bearer $header.$payload.signature"
    }
}
