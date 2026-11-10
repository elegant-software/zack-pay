package com.elegant.software.blitzpay.usersettings.domain

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.JsonCreator
import io.swagger.v3.oas.annotations.media.Schema

@Schema(type = "string", allowableValues = ["en", "de"], enumAsRef = true)
enum class AppLanguage(@get:JsonValue val code: String) {
    EN("en"), DE("de");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromCode(code: String): AppLanguage = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException("App language must be en or de")
    }
}
