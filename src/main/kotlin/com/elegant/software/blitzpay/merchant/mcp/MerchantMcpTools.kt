package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.MerchantContactInfo
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.MerchantVerticalResponse
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.service.MerchantBranchService
import com.elegant.software.blitzpay.merchant.service.MerchantLogoPolicy
import com.elegant.software.blitzpay.merchant.service.MerchantLogoService
import com.elegant.software.blitzpay.merchant.service.MerchantManagementService
import com.elegant.software.blitzpay.merchant.service.MerchantRegistrationService
import com.elegant.software.blitzpay.merchant.service.MerchantVerticalService
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component
import java.util.Base64
import java.util.UUID

@Suppress("unused")
@Component
class MerchantMcpTools(
    private val merchantRegistrationService: MerchantRegistrationService,
    private val merchantManagementService: MerchantManagementService,
    private val merchantLogoService: MerchantLogoService,
    private val merchantBranchService: MerchantBranchService,
    private val merchantVerticalService: MerchantVerticalService,
    private val storageService: StorageService,
) {

    @McpTool(
        name = "merchant_api_guide",
        description = "Call this before starting any merchant, category, or product workflow. " +
            "Returns tool-selection rules and the recommended operation order.",
        annotations = McpTool.McpAnnotations(title = "Merchant API Guide", readOnlyHint = true, openWorldHint = false)
    )
    fun merchantApiGuide(): String = """
        TOOL SELECTION
        ══════════════
        Merchant
          businessType unknown → merchant_list_active_verticals  (discover valid merchant vertical codes first)
          always upsert       → merchant_upsert  (creates or updates, including logo)
          food truck / street seller / circus / pop-up → pass mobility = "MOBILE" to merchant_upsert
          fixed location (shop, restaurant, salon)     → mobility defaults to "BRICK_AND_MORTAR"

        Branches
          1 branch   → branch_id_by_name_or_create
          2+ branches → branches_bulk_upsert  (never loop the single-branch tool; supports all attributes)
          GOOD → provide branch address when known; BEST → provide address plus latitude/longitude for the same branch
          If coordinates are unknown, still provide the branch address when available
          MOBILE merchant branches → address fields are the depot/registered address, NOT the operating location

        Products
          1 product  → product_id_by_name_or_create
          2+ products → products_bulk_upsert  (never loop the single-product tool)
          GOOD → provide product images when available (see PRODUCT IMAGES below)
          For bulk imports, create products first, then use merchant_product_update for images
          read one    → product_get  (by productId or productName; includes inactive products)
          read many   → products_list  (optional categoryName filter; paged with offset/limit)
          change some fields → merchant_product_update  (partial: omitted fields keep their stored values;
                               pass an empty string to clear description or category)

        PRODUCT IMAGES
        ══════════════
        Decide the image source in this order:
          1. Image has a public http(s) URL (merchant website, CDN, marketplace)  → imageUrl
               The server downloads it; up to 15 MB; images over 5 MB are resized automatically.
               The URL must open without login/cookies and must not be a private/internal address.
          2. No URL, and the image is SMALL (original < ~150 KB, base64 < ~200 KB) → imageBase64 + imageContentType
               Typical cases: a thumbnail or logo-sized picture pasted into the chat or read from a local file.
          3. No URL, and the image is larger                                      → do NOT base64 it
               Ask the user for a public link, or skip the image and continue with the product data.
        Never send imageUrl and imageBase64 together. imageContentType is only needed with imageBase64.
        Prefer the original, full-quality URL over thumbnails (avoid "thumb", "small", "w=150" style variants).
        Images must be clear, directly related to the product, and JPEG, PNG, or WebP (WebP over 5 MB is rejected).
        The server rejects technically poor images and says why: shorter side under 400 px, aspect ratio over 4:1,
        blurry, too dark or overexposed, or blank. On rejection, look for a better image of the same product
        (larger original, different photo); do not retry the same image.
        Optional crop (cropX, cropY, cropWidth, cropHeight) is in source-image pixels and works for JPEG/PNG.
        To set or replace only the image of an existing product, call merchant_product_update with merchantId,
        productId, branchId and the image; price, description, category and productCode stay unchanged.
        Find the productId with product_get or products_list.

        Categories
          1 category  → category_id_by_name_or_create
          2+ categories → categories_bulk_create  (never loop the single-category tool)

        TRANSLATIONS (International Menu)
        ═════════════════════════════════
          Products and categories are authored in the merchant's originalMenuLanguage — pass it to merchant_upsert
          (ISO 639-1, the language of the source menu). Write names/descriptions in that language only.
          Additional languages (supportedMenuLanguages) are chosen by the merchant in the admin dashboard; MCP cannot set them.
          Translate only after the products exist:
            catalog_pending_translation(merchantId, language)  → items to translate (missing or stale)
            catalog_translations_bulk_upsert(merchantId, language, items)  → save them; repeat until totalPending is 0
          Items with origin MERCHANT were edited by hand — keep their wording when re-translating.

        RECOMMENDED WORKFLOW ORDER
        ══════════════════════════
        1. merchant_upsert             — create or update the merchant (returns merchantId); set mobility if MOBILE
        2. branches_bulk_upsert        — create or update all branches with full attributes
        3. categories_bulk_create      — (optional) pre-create all categories in one call
        4. products_bulk_upsert        — create all products, referencing categoryName
        5. catalog_pending_translation — (optional) per supported menu language, then catalog_translations_bulk_upsert

        IDEMPOTENCY & RESPONSES
        ═══════════════════════
        • merchant_upsert is idempotent by merchantName — re-running updates the merchant safely.
        • If businessType is unknown, call merchant_list_active_verticals before merchant_upsert.
        • branches_bulk_upsert is idempotent by branchName — re-running updates branches safely.
        • productCode is the idempotency key for products — re-running the same payload is safe.
        • Bulk responses include IDs in all buckets (created / updated / skipped / failed).
        • categoryName is resolved or created automatically; no separate category lookup required.
    """.trimIndent()

    @McpTool(
        name = "merchant_list_active_verticals",
        description = "List valid merchant businessType codes and display names from the active merchant vertical catalog. " +
            "Use this before merchant_upsert when businessType is unknown or needs validation.",
        annotations = McpTool.McpAnnotations(title = "List Active Verticals", readOnlyHint = true, openWorldHint = false)
    )
    fun listActiveMerchantVerticals(): List<MerchantVerticalResponse> =
        merchantVerticalService.listActive()

    @McpTool(
        name = "merchant_id_by_name",
        description = "Get merchant ID by merchant name",
        annotations = McpTool.McpAnnotations(title = "Merchant ID by Name", readOnlyHint = true, openWorldHint = false)
    )
    fun getMerchantIdByName(merchantName: String): String {
        return merchantRegistrationService.findByName(merchantName)?.id?.toString()
            ?: throw IllegalArgumentException("Merchant not found with name: $merchantName")
    }

    @McpTool(
        name = "merchant_upsert",
        description = "Create or update a merchant by name. Always upserts — creates when not found, updates profile when found. " +
            "Pass mobility = \"MOBILE\" for food trucks, street sellers, circuses, pop-up theatres, or any merchant that operates from variable locations. " +
            "Omit mobility (or pass \"BRICK_AND_MORTAR\") for fixed-location merchants. " +
            "Optionally uploads a logo via logoBase64 and logoContentType only; use a clear, high-quality merchant logo in JPEG, PNG, or WebP format. " +
            "Pass originalMenuLanguage (ISO 639-1 code, e.g. \"de\", \"en\", \"tr\") for the language the source menu is written in. " +
            "Returns full merchant details; use the returned merchantId as merchantId in subsequent branch and product calls.",
        annotations = McpTool.McpAnnotations(title = "Merchant Upsert", idempotentHint = true, openWorldHint = false)
    )
    fun upsertMerchant(
        merchantName: String,
        businessType: String? = null,
        operatingCountry: String = "US",
        website: String? = null,
        contactEmail: String? = null,
        contactPhoneNumber: String? = null,
        xHandle: String? = null,
        instagramHandle: String? = null,
        defaultBranchName: String = "Main Branch",
        logoBase64: String? = null,
        logoContentType: String? = null,
        mobility: String? = null,
        originalMenuLanguage: String? = null,
    ): MerchantDetailsResponse {
        val existing = merchantRegistrationService.findByName(merchantName)
        val merchantId: UUID

        if (existing == null) {
            val created = merchantRegistrationService.register(
                RegisterMerchantRequest(
                    merchantName = merchantName,
                    businessType = businessType ?: "RETAIL",
                    operatingCountry = operatingCountry,
                    mobility = mobility?.let(MerchantMobility::valueOf),
                )
            )
            merchantId = created.id
            merchantManagementService.update(
                merchantId,
                UpdateMerchantRequest(
                    website = website,
                    contactEmail = contactEmail,
                    contactPhoneNumber = contactPhoneNumber,
                    contactInfo = if (xHandle != null || instagramHandle != null) MerchantContactInfo(
                        website = website,
                        email = contactEmail,
                        phoneNumber = contactPhoneNumber,
                        xHandle = xHandle,
                        instagramHandle = instagramHandle,
                    ) else null,
                    merchantMobility = mobility?.let(MerchantMobility::valueOf),
                    originalMenuLanguage = originalMenuLanguage,
                )
            )
        } else {
            merchantId = existing.id
            merchantManagementService.update(
                merchantId,
                UpdateMerchantRequest(
                    merchantName = merchantName,
                    businessType = businessType,
                    website = website,
                    contactEmail = contactEmail,
                    contactPhoneNumber = contactPhoneNumber,
                    contactInfo = if (xHandle != null || instagramHandle != null) MerchantContactInfo(
                        website = website,
                        email = contactEmail,
                        phoneNumber = contactPhoneNumber,
                        xHandle = xHandle,
                        instagramHandle = instagramHandle,
                    ) else null,
                    merchantMobility = mobility?.let(MerchantMobility::valueOf),
                    originalMenuLanguage = originalMenuLanguage,
                )
            )
        }

        ensureBranchExists(merchantId, defaultBranchName)

        val logo = merchantLogoOrNull(logoBase64, logoContentType)
        if (logo != null) {
            val key = MerchantLogoPolicy.storageKeyFor(merchantId, logo.first)
            storageService.upload(key, logo.first, logo.second)
            merchantLogoService.attachLogo(merchantId, key)
        }

        return merchantManagementService.get(merchantId)
    }

    private fun merchantLogoOrNull(
        logoBase64: String?,
        logoContentType: String?
    ): Pair<String, ByteArray>? {
        val normalizedBase64 = logoBase64?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedContentType = logoContentType?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedBase64 == null) {
            require(normalizedContentType == null) {
                "logoBase64 is required when logoContentType is provided"
            }
            return null
        }
        val contentType = normalizedContentType
            ?: throw IllegalArgumentException("logoContentType is required when logoBase64 is provided")
        MerchantLogoPolicy.extensionFor(contentType)
        val bytes = Base64.getDecoder().decode(normalizedBase64.substringAfter("base64,", normalizedBase64))
        return contentType to bytes
    }

    private fun ensureBranchExists(merchantId: UUID, defaultBranchName: String) {
        require(defaultBranchName.isNotBlank()) { "defaultBranchName must not be blank" }
        val existingBranch = merchantBranchService.findByNameIncludingInactive(merchantId, defaultBranchName)
        if (existingBranch == null) {
            merchantBranchService.create(
                merchantId,
                CreateBranchRequest(name = defaultBranchName),
                active = false
            )
        }
    }
}
