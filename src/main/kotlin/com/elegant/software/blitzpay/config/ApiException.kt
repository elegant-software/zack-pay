package com.elegant.software.blitzpay.config

import org.springframework.http.HttpStatus

/**
 * An error the API reports to clients. [GlobalExceptionHandler] renders it as a `ProblemDetail`
 * whose `code` property is [code]: clients branch on the code, never on the message text.
 *
 * The code defaults to the status name (`NOT_FOUND`, `CONFLICT`, ...); pass a specific code
 * only where a client needs to tell this error apart from others with the same status.
 */
open class ApiException(
    val status: HttpStatus,
    message: String,
    val code: String = status.name,
    cause: Throwable? = null,
) : RuntimeException(message) {
    // initCause, not the (message, cause) constructor: that one would block a later initCause.
    init { cause?.let(::initCause) }
}
