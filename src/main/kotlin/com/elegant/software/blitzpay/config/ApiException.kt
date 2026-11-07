package com.elegant.software.blitzpay.config

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException

/**
 * An error the API reports to clients. Spring renders it as an RFC 9457 `ProblemDetail`
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
) : ErrorResponseException(
    status,
    ProblemDetail.forStatusAndDetail(status, message).apply { setProperty(CODE, code) },
    cause,
) {
    /** The plain detail, not Spring's "status + detail" summary, so logs and tests read naturally. */
    override val message: String get() = body.detail.orEmpty()

    companion object {
        /** The `ProblemDetail` extension property that carries the code. */
        const val CODE = "code"
    }
}
