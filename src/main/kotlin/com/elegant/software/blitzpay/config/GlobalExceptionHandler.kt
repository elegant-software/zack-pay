package com.elegant.software.blitzpay.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.NoSuchElementException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ProblemDetail> {
        log.warn("Not found: {}", ex.message)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
        problem.title = "Not Found"
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        log.warn("Bad request: {}", ex.message)
        val isConflict = ex.message?.contains("already exists", ignoreCase = true) ?: false
        val status = if (isConflict) HttpStatus.CONFLICT else HttpStatus.BAD_REQUEST

        val problem = ProblemDetail.forStatusAndDetail(status, ex.message ?: "Invalid request")
        problem.title = if (isConflict) "Conflict" else "Bad Request"

        return ResponseEntity.status(status).body(problem)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ProblemDetail> {
        log.error("Unprocessable: {}", ex.message, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Illegal state")
        problem.title = "Unprocessable Entity"
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }
}
