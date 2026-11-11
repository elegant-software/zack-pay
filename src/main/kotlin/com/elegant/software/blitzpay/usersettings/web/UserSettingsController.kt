package com.elegant.software.blitzpay.usersettings.web

import com.elegant.software.blitzpay.usersettings.domain.AppLanguage
import com.elegant.software.blitzpay.usersettings.service.UserSettingsResponse
import com.elegant.software.blitzpay.usersettings.service.UserSettingsService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

data class UpdateUserSettingsRequest(
    val shopperAppLanguage: AppLanguage? = null,
    val merchantAppLanguage: AppLanguage? = null,
)

@io.swagger.v3.oas.annotations.tags.Tag(name = "User Settings")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/user-settings", version = "1")
class UserSettingsController(private val service: UserSettingsService) {
    @Operation(operationId = "getUserSettings", summary = "Read the authenticated account's independent app languages (null until explicitly saved)")
    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt): UserSettingsResponse = service.get(subject(jwt))

    @Operation(operationId = "updateUserSettings", summary = "Update app languages; omitted or null fields and unrelated settings remain unchanged")
    @PatchMapping
    fun update(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: UpdateUserSettingsRequest): UserSettingsResponse =
        service.update(subject(jwt), request.shopperAppLanguage, request.merchantAppLanguage)
    private fun subject(jwt: Jwt): String = jwt.subject?.takeIf { it.isNotBlank() }
        ?: throw com.elegant.software.blitzpay.config.ApiException(
            org.springframework.http.HttpStatus.UNAUTHORIZED, "An authenticated account subject is required",
        )
}
