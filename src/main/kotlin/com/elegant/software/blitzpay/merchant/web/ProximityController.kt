package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.ProximityEventRequest
import com.elegant.software.blitzpay.merchant.api.ProximityResponse
import com.elegant.software.blitzpay.merchant.application.ProximityService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.*
import java.util.Base64

@Tag(name = "Proximity", description = "Mobile geofence enter and exit event recording")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/proximity", version = "1")
class ProximityController(private val proximityService: ProximityService) {
    private val log = LoggerFactory.getLogger(ProximityController::class.java)
    private val objectMapper = jacksonObjectMapper()

    @Operation(summary = "Record a geofence proximity event and return merchant context when applicable")
    @PostMapping
    fun record(
        @RequestBody request: ProximityEventRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<ProximityResponse> {
        val userSubject = extractUserSubject(authorization)
        log.info(
            "Proximity event received: regionId={} event={} deviceId={} authenticated={}",
            request.regionId, request.event, request.deviceId, userSubject != null,
        )
        val result = ResponseEntity.ok(proximityService.record(request, userSubject))
        log.info(
            "Proximity event response: recorded={} action={} merchantId={}",
            result.body?.recorded, result.body?.action, result.body?.merchant?.merchantId,
        )
        return result
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleInvalidRequest(ex: HttpMessageNotReadableException): ResponseEntity<ProblemDetail> {
        log.warn("Invalid proximity request: {}", ex.message)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Invalid request body")
        problem.title = "Unprocessable Entity"
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    private fun extractUserSubject(authorization: String?): String? {
        val bearerToken = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val subject = runCatching {
            val parts = bearerToken.split('.')
            if (parts.size < 2) return@runCatching null
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            objectMapper.readTree(payload).path("sub").asText(null)
        }.getOrNull()
        if (subject.isNullOrBlank()) {
            log.info("Proximity event bearer token did not contain a usable subject; skipping user subject persistence")
            return null
        }
        return subject.take(512)
    }
}
