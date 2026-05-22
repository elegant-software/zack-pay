package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.application.MerchantVerticalService
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult
import io.modelcontextprotocol.spec.McpSchema.PromptMessage
import io.modelcontextprotocol.spec.McpSchema.Role
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.springframework.ai.mcp.annotation.McpArg
import org.springframework.ai.mcp.annotation.McpComplete
import org.springframework.ai.mcp.annotation.McpPrompt
import org.springframework.stereotype.Component

@Suppress("unused")
@Component
class MerchantMcpPrompts(
    private val merchantVerticalService: MerchantVerticalService,
) {

    @McpPrompt(
        name = "merchant_onboarding_prompt",
        title = "Merchant Onboarding",
        description = "Optional onboarding helper prompt. Start by calling merchant_api_guide; if businessType is unknown, use merchant_list_active_verticals as the canonical discovery path."
    )
    fun merchantOnboardingPrompt(
        @McpArg(name = "merchantName", description = "Merchant legal or trading name", required = true)
        merchantName: String,
        @McpArg(
            name = "businessType",
            description = "Merchant vertical code. Use completion suggestions sourced from the active merchant vertical list.",
            required = false
        )
        businessType: String? = null,
        @McpArg(name = "operatingCountry", description = "Merchant operating country code", required = false)
        operatingCountry: String? = null,
    ): GetPromptResult {
        val resolvedBusinessType = businessType?.trim()?.takeIf { it.isNotEmpty() } ?: "<choose businessType>"
        val resolvedCountry = operatingCountry?.trim()?.takeIf { it.isNotEmpty() } ?: "US"

        val message = """
            This prompt is a secondary helper. Start with `merchant_api_guide`.
            
            If businessType is unknown, call `merchant_list_active_verticals` first. Prompt completion can help fill `businessType`, but the tool is the canonical source of valid values.
            
            Then onboard this merchant with `merchant_upsert`.
            
            Proposed merchant_upsert arguments:
            - merchantName: $merchantName
            - businessType: $resolvedBusinessType
            - operatingCountry: $resolvedCountry
        """.trimIndent()

        return GetPromptResult(
            "Merchant onboarding workflow",
            listOf(PromptMessage(Role.ASSISTANT, TextContent(message)))
        )
    }

    @McpComplete(prompt = "merchant_onboarding_prompt")
    fun completeMerchantOnboardingArgument(argument: CompleteRequest.CompleteArgument): List<String> {
        if (argument.name() != "businessType" && argument.name() != "merchantVertical") {
            return emptyList()
        }

        val prefix = argument.value().trim()
        return merchantVerticalService.listActive()
            .asSequence()
            .map { it.code }
            .filter { prefix.isEmpty() || it.startsWith(prefix, ignoreCase = true) }
            .take(25)
            .toList()
    }
}
