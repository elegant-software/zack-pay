package com.elegant.software.blitzpay.merchant.iam.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * [termsVersion] names the text in `resources/legal/<lang>/` (Terms incl. DPA, Privacy Policy) as a whole.
 * Bump it in the same commit that changes any of those files; signup only accepts the current one.
 */
@ConfigurationProperties(prefix = "blitzpay.legal")
data class LegalProperties(val termsVersion: String, val apiBaseUrl: String)

data class LegalInfo(val termsVersion: String, val language: String, val termsUrl: String, val privacyUrl: String)

/** Public: the apps show these links on signup and in Account → Privacy, in the user's language. */
@Tag(name = "Legal", description = "Current Terms and Privacy Policy, per language")
@RestController
@EnableConfigurationProperties(LegalProperties::class)
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/legal", version = "1")
class LegalController(private val legal: LegalProperties) {

    @Operation(summary = "Current terms version and document links; unknown languages fall back to English")
    @GetMapping
    fun info(@RequestParam(required = false) lang: String?): LegalInfo {
        val language = lang?.lowercase()?.takeIf { find(it, "terms") != null } ?: FALLBACK
        val base = "${legal.apiBaseUrl.trimEnd('/')}/v1/legal/$language"
        return LegalInfo(legal.termsVersion, language, "$base/terms", "$base/privacy")
    }

    @Operation(summary = "A legal document as HTML")
    @GetMapping("/{lang}/{doc}", produces = [MediaType.TEXT_HTML_VALUE])
    fun document(@PathVariable lang: String, @PathVariable doc: String): ResponseEntity<Resource> =
        find(lang.lowercase(), doc)?.let { ResponseEntity.ok().contentType(HTML).body(it) }
            ?: ResponseEntity.notFound().build()

    private fun find(lang: String, doc: String): Resource? {
        if (!LANG.matches(lang) || doc !in DOCS) return null
        return ClassPathResource("legal/$lang/$doc.html").takeIf { it.exists() }
    }

    private companion object {
        const val FALLBACK = "en"
        val LANG = Regex("^[a-z]{2}$")
        val DOCS = setOf("terms", "privacy")
        val HTML = MediaType("text", "html", Charsets.UTF_8)
    }
}
