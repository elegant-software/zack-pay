package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.iam.service.KioskService
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.web.MerchantCallerResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

class KioskAccessDeniedException(message: String) : ApiException(HttpStatus.FORBIDDEN, message)

/**
 * Kiosk Login and Kiosk Settings management for a Branch, used by the web admin. Same caller rule as the Kitchen
 * Login: a Merchant Manager of the Branch's Merchant, or a platform admin.
 */
@Tag(name = "Kiosk Access", description = "Enable, reset and disable the Kiosk Login of a Branch and manage its Kiosk Settings")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchant/branches/{branchId}/kiosk", version = "1")
class KioskAccessController(
    private val service: KioskService,
    private val branchRepository: MerchantBranchRepository,
    private val callerResolver: MerchantCallerResolver,
) {
    @Operation(summary = "Enable the Kiosk for the Branch, or reset its login; returns a new password once")
    @PostMapping("/enable")
    fun enable(
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskLoginResponse> = noStore(service.enable(authorizedBranch(branchId, auth, jwt)))

    @Operation(summary = "Disable the Branch's Kiosk Login and revoke its Kiosk sessions")
    @PostMapping("/disable")
    fun disable(
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskStatusResponse> = noStore(service.disable(authorizedBranch(branchId, auth, jwt)))

    @Operation(summary = "Kiosk Login status and Kiosk Settings of the Branch (never includes the password)")
    @GetMapping
    fun status(
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskStatusResponse> = noStore(service.status(authorizedBranch(branchId, auth, jwt)))

    @Operation(summary = "Replace the Branch's Kiosk Settings")
    @PutMapping("/settings")
    fun updateSettings(
        @PathVariable branchId: UUID,
        @RequestBody settings: KioskSettings,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskSettings> = noStore(service.updateSettings(authorizedBranch(branchId, auth, jwt), settings))

    private fun authorizedBranch(branchId: UUID, auth: String?, jwt: Jwt?): MerchantBranch {
        val branch = branchRepository.findById(branchId).orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (PlatformAdminAccess.isPlatformAdmin(jwt)) return branch
        val isManager = runCatching { callerResolver.resolve(auth, branch.merchantId).isManagerFor(branch.merchantId) }
            .getOrDefault(false)
        if (!isManager) throw KioskAccessDeniedException("Caller is neither a Merchant Manager for this Branch's Merchant nor a platform admin")
        return branch
    }

    private fun <T : Any> noStore(body: T): ResponseEntity<T> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body)
}
