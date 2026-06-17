package com.elegant.software.blitzpay.voice.web

import com.elegant.software.blitzpay.voice.api.VoiceGateway
import com.elegant.software.blitzpay.voice.config.VoiceProperties
import com.elegant.software.blitzpay.voice.service.MissingAudioException
import com.elegant.software.blitzpay.voice.service.MissingAuthorizationException
import com.elegant.software.blitzpay.voice.service.PayloadTooLargeException
import com.elegant.software.blitzpay.voice.service.UnsupportedAudioFormatException
import com.elegant.software.blitzpay.voice.service.VoiceAudioSubmission
import com.elegant.software.blitzpay.voice.service.VoiceException
import com.elegant.software.blitzpay.voice.service.VoiceTranscriptionResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.Base64

@Tag(name = "Voice", description = "Voice query processing for authenticated mobile clients")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/voice", version = "1")
class VoiceQueryController(
    private val voiceGateway: VoiceGateway,
    private val properties: VoiceProperties,
) {
    private val objectMapper = jacksonObjectMapper()

    @Operation(summary = "Submit a voice recording and receive a transcription")
    @PostMapping("/query", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun query(
        @RequestPart("audio", required = false) audio: MultipartFile?,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<VoiceTranscriptionResponse> {
        val callerSubject = extractCallerSubject(authorization)
        val audioPart = audio ?: throw MissingAudioException()
        val contentType = audioPart.contentType ?: throw MissingAudioException()

        if (contentType !in properties.acceptedContentTypes) throw UnsupportedAudioFormatException(contentType)

        val bytes = audioPart.bytes
        if (bytes.size.toLong() > properties.maxUploadBytes) throw PayloadTooLargeException(properties.maxUploadBytes)

        val response = voiceGateway.process(
            VoiceAudioSubmission(
                bytes = bytes,
                contentType = contentType,
                filename = audioPart.originalFilename,
                sizeBytes = bytes.size.toLong(),
                callerSubject = callerSubject,
            )
        )
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response)
    }

    @ExceptionHandler(VoiceException::class)
    fun handleVoiceException(ex: VoiceException): ResponseEntity<ProblemDetail> {
        val status = HttpStatus.valueOf(ex.statusCode)
        val problem = ProblemDetail.forStatusAndDetail(status, ex.message)
        problem.title = status.reasonPhrase
        problem.setProperty("reason", ex.reason)
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem)
    }

    private fun extractCallerSubject(authorization: String?): String {
        val bearerToken = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw MissingAuthorizationException()
        val parts = bearerToken.split('.')
        if (parts.size < 2) throw MissingAuthorizationException()
        val payload = runCatching { String(Base64.getUrlDecoder().decode(parts[1])) }
            .getOrElse { throw MissingAuthorizationException() }
        val subject = runCatching { objectMapper.readTree(payload).path("sub").asText(null) }.getOrNull()
        return subject?.takeIf { it.isNotBlank() }?.take(512) ?: throw MissingAuthorizationException()
    }
}
