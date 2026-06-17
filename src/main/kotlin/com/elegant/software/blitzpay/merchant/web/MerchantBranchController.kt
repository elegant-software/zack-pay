package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.BranchResponse
import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.UpdateBranchRequest
import com.elegant.software.blitzpay.merchant.service.MerchantBranchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class BranchErrorResponse(val error: String)
data class SetBranchImageRequest(val storageKey: String)

@Tag(name = "Merchant Branches", description = "Manage branches under a merchant")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/branches", version = "1")
class MerchantBranchController(private val merchantBranchService: MerchantBranchService) {

    @Operation(summary = "Create a new branch under a merchant")
    @PostMapping
    fun create(
        @PathVariable merchantId: UUID,
        @RequestBody request: CreateBranchRequest,
    ): ResponseEntity<BranchResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(merchantBranchService.create(merchantId, request))

    @Operation(summary = "List branches for a merchant")
    @GetMapping
    fun list(@PathVariable merchantId: UUID): ResponseEntity<List<BranchResponse>> =
        ResponseEntity.ok(merchantBranchService.list(merchantId))

    @Operation(summary = "Get a branch by ID")
    @GetMapping("/{branchId}")
    fun get(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
    ): ResponseEntity<BranchResponse> =
        ResponseEntity.ok(merchantBranchService.get(merchantId, branchId))

    @Operation(summary = "Update a branch under a merchant")
    @PutMapping("/{branchId}")
    fun update(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestBody request: UpdateBranchRequest,
    ): ResponseEntity<BranchResponse> =
        ResponseEntity.ok(merchantBranchService.update(merchantId, branchId, request))

    @Operation(summary = "Set branch image")
    @PutMapping("/{branchId}/image")
    fun setImage(
        @PathVariable merchantId: UUID,
        @PathVariable branchId: UUID,
        @RequestBody request: SetBranchImageRequest,
    ): ResponseEntity<BranchResponse> =
        ResponseEntity.ok(merchantBranchService.updateImage(merchantId, branchId, request.storageKey))
}
