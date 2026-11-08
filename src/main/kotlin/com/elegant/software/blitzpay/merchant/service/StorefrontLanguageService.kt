package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.StorefrontLanguageResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.util.Locale

/** System-owned catalogue shipped with the code; deliberately independent of merchant menu data. */
@Service
class StorefrontLanguageService {
    private val messages = ResourceBundleMessageSource().apply {
        setBasename("i18n/messages")
        setDefaultEncoding("UTF-8")
        setFallbackToSystemLocale(false)
    }

    private val languages: List<StorefrontLanguageResponse> =
        ClassPathResource("i18n/storefront-languages.json").inputStream.use { input ->
            val source = jacksonObjectMapper().readTree(input)
            require(source.isArray && !source.isEmpty) { "Storefront language definitions must be a non-empty array" }
            source.map { entry ->
                val code = entry.path("code").asText()
                val flag = entry.path("flagCode").asText()
                val direction = entry.path("direction").asText()
                require(Regex("^[a-z]{2,3}(-[A-Za-z0-9]+)*$").matches(code)) { "Invalid storefront language code" }
                require(Regex("^[A-Z]{2}$").matches(flag)) { "Invalid storefront representative flag" }
                require(direction in setOf("ltr", "rtl")) { "Invalid storefront language direction" }
                StorefrontLanguageResponse(
                    code = code,
                    displayName = messages.getMessage("language.$code", null, Locale.ENGLISH),
                    nativeName = messages.getMessage("language.$code", null, Locale.forLanguageTag(code)),
                    flagCode = flag,
                    direction = direction,
                )
            }.also { entries ->
                require(entries.map { it.code }.distinct().size == entries.size) { "Duplicate storefront language code" }
            }
        }

    fun catalogue(): List<StorefrontLanguageResponse> = languages
}
