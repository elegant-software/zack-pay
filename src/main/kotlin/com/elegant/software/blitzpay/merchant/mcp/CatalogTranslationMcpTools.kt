package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.CatalogTranslationInput
import com.elegant.software.blitzpay.merchant.api.CatalogTranslationUpsertResult
import com.elegant.software.blitzpay.merchant.api.PendingTranslationsResponse
import com.elegant.software.blitzpay.merchant.service.CatalogTranslationService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.util.UUID

@Suppress("unused")
@Component
class CatalogTranslationMcpTools(
    private val catalogTranslationService: CatalogTranslationService,
) {

    @McpTool(
        name = "catalog_pending_translation",
        description = "List the merchant's products and categories that need translating into one Supported Menu Language: " +
            "items with no translation yet, and items whose translation is stale because the original text changed. " +
            "Returns the Original Menu Language text to translate from. Stale items include the current translation; " +
            "when origin is MERCHANT the merchant edited it by hand, so keep their wording and terminology and only adapt what changed. " +
            "Supported Menu Languages are configured by the merchant in the admin dashboard, not via MCP. " +
            "Translate the items, then call catalog_translations_bulk_upsert; repeat until totalPending is 0.",
        annotations = McpTool.McpAnnotations(title = "Catalog: Pending Translations", readOnlyHint = true, openWorldHint = false)
    )
    fun pendingTranslations(
        @McpToolParam(description = "Merchant id") merchantId: String,
        @McpToolParam(description = "Target language code from the merchant's supportedMenuLanguages, e.g. \"en\"") language: String,
        @McpToolParam(required = false, description = "Maximum items to return (1-200, default 100)") limit: Int? = null,
    ): PendingTranslationsResponse {
        val resolvedLimit = limit ?: DEFAULT_LIMIT
        require(resolvedLimit in 1..MAX_BATCH) { "limit must be between 1 and $MAX_BATCH" }
        return catalogTranslationService.pending(UUID.fromString(merchantId), language, resolvedLimit)
    }

    @McpTool(
        name = "catalog_translations_bulk_upsert",
        description = "Save translations of products and categories into one Supported Menu Language. " +
            "Each item has type PRODUCT or CATEGORY, the item id from catalog_pending_translation, the translated name, " +
            "and for products the translated description (omit when the original has none). " +
            "Translate faithfully from the Original Menu Language; keep dish names that are proper nouns as they are. " +
            "Translations the merchant edited by hand are skipped unless the original text changed since. " +
            "Returns created, updated, skipped and failed items. Max 200 items per call.",
        annotations = McpTool.McpAnnotations(title = "Catalog: Bulk Upsert Translations", idempotentHint = true, openWorldHint = false)
    )
    fun bulkUpsertTranslations(
        @McpToolParam(description = "Merchant id") merchantId: String,
        @McpToolParam(description = "Target language code from the merchant's supportedMenuLanguages, e.g. \"en\"") language: String,
        @McpToolParam(description = "Translated items: type (PRODUCT or CATEGORY), id, name, description (products only)")
        items: List<CatalogTranslationInput>,
    ): CatalogTranslationUpsertResult {
        require(items.size <= MAX_BATCH) { "Batch size must not exceed $MAX_BATCH items" }
        return catalogTranslationService.bulkUpsertMachine(UUID.fromString(merchantId), language, items)
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_BATCH = 200
    }
}
