package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.BranchResponse
import com.elegant.software.blitzpay.merchant.api.BulkBranchInput
import com.elegant.software.blitzpay.merchant.api.BulkBranchUpsertResult
import com.elegant.software.blitzpay.merchant.api.BulkFailedItem
import com.elegant.software.blitzpay.merchant.api.BulkSkippedItem
import com.elegant.software.blitzpay.merchant.application.MerchantBranchService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component
import java.util.LinkedHashSet
import java.util.UUID

@Suppress("unused")
@Component
class BranchMcpTools(
    private val merchantBranchService: MerchantBranchService,
) {

    @McpTool(
        name = "branch_id_by_name",
        description = "Get branch ID by branch name and merchant ID"
    )
    fun getBranchIdByName(merchantId: String, branchName: String): String {
        return merchantBranchService.findByNameIncludingInactive(UUID.fromString(merchantId), branchName)?.id?.toString()
            ?: throw IllegalArgumentException("Branch not found with name: $branchName")
    }

    @McpTool(
        name = "branch_id_by_name_or_create",
        description = "Get or create branch ID by branch name and merchant ID. Optional address, latitude, longitude, geofenceRadiusMeters, and googlePlaceId update the branch when provided. " +
            "GOOD: provide branch address when known. BEST: provide address plus latitude/longitude for the same branch. If coordinates are unknown, still provide the branch address when available. " +
            "For MOBILE merchants the address is the depot or registered business address, not the operating location."
    )
    fun getOrCreateBranchId(
        merchantId: String,
        branchName: String,
        addressLine1: String? = null,
        addressLine2: String? = null,
        city: String? = null,
        postalCode: String? = null,
        country: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        geofenceRadiusMeters: Int? = null,
        googlePlaceId: String? = null
    ): String {
        require((latitude == null) == (longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        geofenceRadiusMeters?.let { require(it > 0) { "geofenceRadiusMeters must be positive" } }

        val mId = UUID.fromString(merchantId)
        return merchantBranchService.upsertByName(
            mId,
            branchName = branchName,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            postalCode = postalCode,
            country = country,
            latitude = latitude,
            longitude = longitude,
            geofenceRadiusMeters = geofenceRadiusMeters,
            googlePlaceId = googlePlaceId
        ).id.toString()
    }

    @McpTool(
        name = "branches_bulk_upsert",
        description = "PREFERRED tool when working with 2 or more branches — use this instead of calling branch_id_by_name_or_create in a loop. " +
            "Upserts branches by name: creates when not found, updates all provided attributes when found. " +
            "Supported attributes: address, contact details, geofence/location. " +
            "GOOD: provide branch address when known. BEST: provide address plus latitude/longitude for the same branch. If coordinates are unknown, still provide the branch address when available. " +
            "For MOBILE merchants the address fields are the depot or registered business address, not the operating location. " +
            "Returns created, updated, skipped (within-batch duplicates), and failed items with their IDs. " +
            "Max 200 branches per call."
    )
    fun bulkUpsertBranches(
        merchantId: String,
        branches: List<BulkBranchInput>
    ): BulkBranchUpsertResult {
        require(branches.size <= 200) { "Batch size must not exceed 200 items" }
        val mId = UUID.fromString(merchantId)
        val created = mutableListOf<BranchResponse>()
        val updated = mutableListOf<BranchResponse>()
        val skipped = mutableListOf<BulkSkippedItem>()
        val failed = mutableListOf<BulkFailedItem>()
        val seenNames = LinkedHashSet<String>()

        for (input in branches) {
            val normalizedName = input.branchName.trim()
            if (!seenNames.add(normalizedName.lowercase())) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "duplicate within batch"))
                continue
            }
            require((input.latitude == null) == (input.longitude == null)) {
                "latitude and longitude must both be provided or both be omitted for branch: $normalizedName"
            }

            val existingBranch = merchantBranchService.findByNameIncludingInactive(mId, normalizedName)
            runCatching {
                merchantBranchService.upsertByName(
                    merchantId = mId,
                    branchName = normalizedName,
                    addressLine1 = input.addressLine1,
                    addressLine2 = input.addressLine2,
                    city = input.city,
                    postalCode = input.postalCode,
                    country = input.country,
                    contactFullName = input.contactFullName,
                    contactEmail = input.contactEmail,
                    contactPhoneNumber = input.contactPhoneNumber,
                    latitude = input.latitude,
                    longitude = input.longitude,
                    geofenceRadiusMeters = input.geofenceRadiusMeters,
                    googlePlaceId = input.googlePlaceId,
                )
            }.onSuccess { response ->
                if (existingBranch == null) created.add(response) else updated.add(response)
            }.onFailure { ex ->
                failed.add(BulkFailedItem(name = normalizedName, reason = ex.message ?: "Unknown error"))
            }
        }
        return BulkBranchUpsertResult(created = created, updated = updated, skipped = skipped, failed = failed)
    }
}
