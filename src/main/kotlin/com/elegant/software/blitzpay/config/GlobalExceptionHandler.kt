package com.elegant.software.blitzpay.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Every API error is an RFC 9457 `ProblemDetail` with an extra `code` property, e.g.
 * `{"type":"about:blank","title":"Not Found","status":404,"detail":"Order not found: X","code":"NOT_FOUND"}`.
 * Clients branch on `code`; `detail` is human-readable and may change.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ProblemDetail> {
        if (ex.status.is5xxServerError) log.error("API error {}: {}", ex.code, ex.message, ex)
        else log.warn("API error {}: {}", ex.code, ex.message)
        return problem(ex.status, ex.message, ex.code)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ProblemDetail> {
        log.warn("Not found: {}", ex.message)
        return problem(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        log.warn("Bad request: {}", ex.message)
        val isConflict = ex.message?.contains("already exists", ignoreCase = true) ?: false
        return problem(if (isConflict) HttpStatus.CONFLICT else HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ProblemDetail> {
        log.error("Unprocessable: {}", ex.message, ex)
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Illegal state")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unhandled error", ex)
        // Never echo internal messages to clients.
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.")
    }

    /** Framework errors (bad JSON, validation, 405, 415, ...) get the same `code` property. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        (body as? ProblemDetail)?.setProperty("code", HttpStatus.resolve(statusCode.value())?.name ?: statusCode.value().toString())
        return super.handleExceptionInternal(ex, body, headers, statusCode, request)
    }

    companion object {
        fun problem(status: HttpStatus, detail: String?, code: String = status.name): ResponseEntity<ProblemDetail> {
            val problem = ProblemDetail.forStatusAndDetail(status, detail)
            problem.setProperty("code", code)
            // Explicit content type: SSE endpoints only produce text/event-stream, which can't carry the error body.
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem)
        }
    }
}
