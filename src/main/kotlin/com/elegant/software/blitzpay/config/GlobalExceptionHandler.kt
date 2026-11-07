package com.elegant.software.blitzpay.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Every API error is an RFC 9457 `ProblemDetail` rendered by Spring, plus a `code` property, e.g.
 * `{"type":"about:blank","title":"Not Found","status":404,"detail":"Order not found: X","instance":"/v1/...","code":"NOT_FOUND"}`.
 * Clients branch on `code`; `detail` is human-readable and may change.
 *
 * Spring's [ResponseEntityExceptionHandler] already renders [ApiException] (an `ErrorResponseException`)
 * and its own framework errors; this class only maps plain JDK exceptions and adds `code` and logging.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException, request: WebRequest): ResponseEntity<Any>? =
        render(ApiException(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found", cause = ex), request)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: WebRequest): ResponseEntity<Any>? {
        val isConflict = ex.message?.contains("already exists", ignoreCase = true) ?: false
        val status = if (isConflict) HttpStatus.CONFLICT else HttpStatus.BAD_REQUEST
        return render(ApiException(status, ex.message ?: "Invalid request", cause = ex), request)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException, request: WebRequest): ResponseEntity<Any>? =
        render(ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Illegal state", cause = ex), request)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: WebRequest): ResponseEntity<Any>? =
        // Never echo internal messages to clients.
        render(ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", cause = ex), request)

    /** Every error passes through here: add the default `code` and log once. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        if (body is ProblemDetail && body.properties?.containsKey(ApiException.CODE) != true) {
            body.setProperty(ApiException.CODE, HttpStatus.resolve(statusCode.value())?.name ?: statusCode.value().toString())
        }
        val code = (body as? ProblemDetail)?.properties?.get(ApiException.CODE)
        if (statusCode.is5xxServerError) log.error("API error {} {}: {}", statusCode.value(), code, ex.message, ex.cause ?: ex)
        else log.warn("API error {} {}: {}", statusCode.value(), code, ex.message)
        return super.handleExceptionInternal(ex, body, headers, statusCode, request)
    }

    private fun render(ex: ApiException, request: WebRequest): ResponseEntity<Any>? =
        handleErrorResponseException(ex, HttpHeaders(), ex.statusCode, request)
}
