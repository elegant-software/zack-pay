package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.MerchantBusinessProfileRequest
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.MerchantPrimaryContactRequest
import com.elegant.software.blitzpay.merchant.api.MerchantVerticalResponse
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.application.MerchantBranchService
import com.elegant.software.blitzpay.merchant.application.MerchantLogoPolicy
import com.elegant.software.blitzpay.merchant.application.MerchantLogoService
import com.elegant.software.blitzpay.merchant.application.MerchantManagementService
import com.elegant.software.blitzpay.merchant.application.MerchantRegistrationService
import com.elegant.software.blitzpay.merchant.application.MerchantVerticalService
import com.elegant.software.blitzpay.merchant.domain.MerchantMobility
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
        annotations = McpTool.McpAnnotations(title = "Merchant API Guide")
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
          GOOD → provide product images when available using imageBase64 + imageContentType only
          Product images must be clear, high-quality, directly related to the product, JPEG/PNG/WebP, and 5 MB or smaller
          For bulk imports, create products first, then use merchant_product_update for images

        Categories
          1 category  → category_id_by_name_or_create
          2+ categories → categories_bulk_create  (never loop the single-category tool)

        RECOMMENDED WORKFLOW ORDER
        ══════════════════════════
        1. merchant_upsert             — create or update the merchant (returns applicationId); set mobility if MOBILE
        2. branches_bulk_upsert        — create or update all branches with full attributes
        3. categories_bulk_create      — (optional) pre-create all categories in one call
        4. products_bulk_upsert        — create all products, referencing categoryName

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
        annotations = McpTool.McpAnnotations(title = "List Active Verticals")
    )
    fun listActiveMerchantVerticals(): List<MerchantVerticalResponse> =
        merchantVerticalService.listActive()

    @McpTool(
        name = "merchant_id_by_name",
        description = "Get merchant ID by merchant name",
        annotations = McpTool.McpAnnotations(title = "Merchant ID by Name")
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
            "Returns full merchant details; use the returned applicationId as merchantId in subsequent branch and product calls.",
        annotations = McpTool.McpAnnotations(title = "Merchant Upsert")
    )
    fun upsertMerchant(
        merchantName: String,
        registrationNumber: String? = null,
        businessType: String? = null,
        operatingCountry: String = "US",
        primaryBusinessAddress: String? = null,
        contactFullName: String? = null,
        contactEmail: String? = null,
        contactPhoneNumber: String? = null,
        defaultBranchName: String = "Main Branch",
        logoBase64: String? = null,
        logoContentType: String? = null,
        mobility: String? = null,
    ): MerchantDetailsResponse {
        val existing = merchantRegistrationService.findByName(merchantName)
        val merchantId: UUID

        if (existing == null) {
            val normalizedRegistrationNumber = registrationNumber?.trim()?.takeIf { it.isNotEmpty() }
                ?: "MCP-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
            val resolvedEmail = contactEmail?.trim()?.takeIf { it.isNotEmpty() }
                ?: "${merchantName.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')}.merchant@example.com"
            val created = merchantRegistrationService.registerDraft(
                RegisterMerchantRequest(
                    businessProfile = MerchantBusinessProfileRequest(
                        legalBusinessName = merchantName,
                        businessType = businessType ?: "RETAIL",
                        registrationNumber = normalizedRegistrationNumber,
                        operatingCountry = operatingCountry,
                        primaryBusinessAddress = primaryBusinessAddress ?: "Unknown",
                        merchantMobility = mobility?.let(MerchantMobility::valueOf),
                    ),
                    primaryContact = MerchantPrimaryContactRequest(
                        fullName = contactFullName ?: "Merchant Owner",
                        email = resolvedEmail,
                        phoneNumber = contactPhoneNumber ?: "0000000000"
                    )
                )
            )
            merchantId = created.id
        } else {
            merchantId = existing.id
            val current = merchantManagementService.get(merchantId)
            merchantManagementService.update(
                merchantId,
                UpdateMerchantRequest(
                    legalBusinessName = merchantName,
                    businessType = businessType,
                    primaryBusinessAddress = primaryBusinessAddress ?: current.primaryBusinessAddress,
                    contactFullName = contactFullName ?: current.contactFullName,
                    contactEmail = contactEmail ?: current.contactEmail,
                    contactPhoneNumber = contactPhoneNumber ?: current.contactPhoneNumber,
                    merchantMobility = mobility?.let(MerchantMobility::valueOf),
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
