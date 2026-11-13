package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.config.ApiException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Keycloak refused an admin call while provisioning a Kitchen or Kiosk Login: the backend's service account lacks a realm-management role. */
// Ahead of GlobalExceptionHandler, which would turn this into a 500.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [KitchenAccessController::class, KioskAccessController::class])
class KeycloakAdminErrorHandler {
    private val log = LoggerFactory.getLogger(KeycloakAdminErrorHandler::class.java)

    @ExceptionHandler(jakarta.ws.rs.ForbiddenException::class)
    fun keycloakForbidden(ex: jakarta.ws.rs.ForbiddenException): ResponseEntity<ProblemDetail> {
        log.error("Keycloak admin call forbidden; grant the service account realm-management roles manage-clients, manage-users, query-groups", ex)
        val error = ApiException(
            HttpStatus.BAD_GATEWAY,
            "Keycloak refused the request: the backend service account is missing realm-management roles (manage-clients, manage-users, query-groups)",
        )
        return ResponseEntity.of(error.body).build()
    }
}
