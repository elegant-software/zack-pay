package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.merchant.iam.service.AccountExport
import com.elegant.software.blitzpay.merchant.iam.service.AccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The signed-in user's GDPR rights: always acts on the JWT subject, never on an id from the request. */
@Tag(name = "Account", description = "Data export and account deletion for the signed-in user (GDPR)")
@RestController
@Profile("!contract-test")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/me", version = "1")
class AccountController(private val service: AccountService) {

    @Operation(summary = "Download everything stored about the signed-in account (GDPR Art. 15/20)")
    @GetMapping("/data-export")
    fun export(@AuthenticationPrincipal jwt: Jwt): AccountExport = service.export(UUID.fromString(jwt.subject))

    @Operation(summary = "Delete the signed-in account and its personal data (GDPR Art. 17)")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt) = service.delete(UUID.fromString(jwt.subject))
}
