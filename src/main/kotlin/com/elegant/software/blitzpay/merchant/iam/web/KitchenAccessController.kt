package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.iam.service.KitchenAccessService
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

class KitchenAccessDeniedException(message: String) : ApiException(HttpStatus.FORBIDDEN, message)

/**
 * Kitchen Login provisioning for a Branch, used by the web admin. A Merchant Manager of the Branch's Merchant
 * (the rule used for Branch-scoped management such as opening hours and appearances) or a platform admin
 * (realm role `blitzpay-admin`, see [PlatformAdminAccess]) may call it.
 */
@Tag(name = "Kitchen Access", description = "Enable, reset and disable the Kitchen Login of a Branch")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchant/branches/{branchId}/kitchen", version = "1")
class KitchenAccessController(
    private val service: KitchenAccessService,
    private val branchRepository: MerchantBranchRepository,
    private val callerResolver: MerchantCallerResolver,
) {
    @Operation(summary = "Enable the Kitchen Screen for the Branch, or reset its login; sets the given or a generated password and returns it once")
    @PostMapping("/enable")
    fun enable(
        @PathVariable branchId: UUID,
        @RequestBody(required = false) request: DeviceLoginPasswordRequest?,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KitchenLoginResponse> = noStore(service.enable(authorizedBranch(branchId, auth, jwt), request?.password))

    @Operation(summary = "Disable the Branch's Kitchen Login and revoke its sessions, offline sessions included")
    @PostMapping("/disable")
    fun disable(
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KitchenAccessStatusResponse> = noStore(service.disable(authorizedBranch(branchId, auth, jwt)))

    @Operation(summary = "Kitchen Login status of the Branch (never includes the password)")
    @GetMapping
    fun status(
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KitchenAccessStatusResponse> = noStore(service.status(authorizedBranch(branchId, auth, jwt)))

    private fun authorizedBranch(branchId: UUID, auth: String?, jwt: Jwt?): MerchantBranch {
        val branch = branchRepository.findById(branchId).orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (PlatformAdminAccess.isPlatformAdmin(jwt)) return branch
        val isManager = runCatching { callerResolver.resolve(auth, branch.merchantId).isManagerFor(branch.merchantId) }
            .getOrDefault(false)
        if (!isManager) throw KitchenAccessDeniedException("Caller is neither a Merchant Manager for this Branch's Merchant nor a platform admin")
        return branch
    }

    private fun <T : Any> noStore(body: T): ResponseEntity<T> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body)
}
