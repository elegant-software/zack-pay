package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.ProviderAccountResponse
import com.elegant.software.blitzpay.merchant.service.MerchantProviderAccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Merchant Provider Accounts", description = "Manage which Payment Service Providers are enabled for a merchant")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/provider-accounts", version = "1")
class MerchantProviderAccountController(
    private val merchantProviderAccountService: MerchantProviderAccountService,
) {

    @Operation(summary = "List all provider accounts for a merchant")
    @GetMapping
    fun list(@PathVariable merchantId: UUID): List<ProviderAccountResponse> =
        merchantProviderAccountService.list(merchantId)

    @Operation(summary = "Enable a PSP for a merchant — creates the provider account record if it does not exist")
    @PutMapping("/{pspCode}")
    fun enable(
        @PathVariable merchantId: UUID,
        @PathVariable pspCode: String,
    ): ProviderAccountResponse =
        merchantProviderAccountService.enable(merchantId, pspCode.uppercase())

    @Operation(summary = "Disable a PSP for a merchant — sets enabled=false without deleting the record")
    @DeleteMapping("/{pspCode}")
    fun disable(
        @PathVariable merchantId: UUID,
        @PathVariable pspCode: String,
    ): ResponseEntity<Void> {
        merchantProviderAccountService.disable(merchantId, pspCode.uppercase())
        return ResponseEntity.noContent().build()
    }
}
