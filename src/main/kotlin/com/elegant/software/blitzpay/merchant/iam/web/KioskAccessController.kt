package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.service.KioskAccessService
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
import org.springframework.web.bind.annotation.DeleteMapping
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
 * The Kiosks of a Branch and their Kiosk Settings, used by the web admin. Same caller rule as the Kitchen Login: a
 * Merchant Manager of the Branch's Merchant, or a platform admin. A Kiosk itself may only read its Branch's settings.
 */
@Tag(name = "Kiosk Access", description = "Add, enable, reset, disable and remove the Kiosks of a Branch and manage its Kiosk Settings")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchant/branches/{branchId}", version = "1")
class KioskAccessController(
    private val access: KioskAccessService,
    private val settings: KioskService,
    private val branchRepository: MerchantBranchRepository,
    private val callerResolver: MerchantCallerResolver,
) {
    @Operation(summary = "The Kiosks of the Branch (never includes passwords)")
    @GetMapping("/kiosks")
    fun list(
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<List<KioskResponse>> = noStore(access.list(managedBranch(branchId, auth, jwt)))

    @Operation(summary = "Add a Kiosk and provision its Kiosk Login; returns the password once")
    @PostMapping("/kiosks")
    fun create(
        @PathVariable branchId: UUID,
        @RequestBody(required = false) request: CreateKioskRequest?,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskLoginResponse> {
        val login = access.create(managedBranch(branchId, auth, jwt), request?.name, request?.password)
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(login)
    }

    @Operation(summary = "Enable the Kiosk's login again, or reset it; sets the given or a generated password and returns it once")
    @PostMapping("/kiosks/{kioskId}/enable")
    fun enable(
        @PathVariable branchId: UUID,
        @PathVariable kioskId: UUID,
        @RequestBody(required = false) request: DeviceLoginPasswordRequest?,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskLoginResponse> = noStore(access.enable(managedBranch(branchId, auth, jwt), kioskId, request?.password))

    @Operation(summary = "Disable the Kiosk's login and revoke its sessions, offline sessions included")
    @PostMapping("/kiosks/{kioskId}/disable")
    fun disable(
        @PathVariable branchId: UUID,
        @PathVariable kioskId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskResponse> = noStore(access.disable(managedBranch(branchId, auth, jwt), kioskId))

    @Operation(summary = "Remove the Kiosk and its Kiosk Login")
    @DeleteMapping("/kiosks/{kioskId}")
    fun delete(
        @PathVariable branchId: UUID,
        @PathVariable kioskId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<Void> {
        access.delete(managedBranch(branchId, auth, jwt), kioskId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Kiosk Settings of the Branch; also readable by the Branch's Kiosks")
    @GetMapping("/kiosk/settings")
    fun settings(
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskSettings> {
        val branch = branch(branchId)
        if (!(settings.isKioskToken(jwt) && isBranchMember(jwt, branchId))) authorizeManager(branch, auth, jwt)
        return noStore(settings.settings(branch.id))
    }

    @Operation(summary = "Replace the Branch's Kiosk Settings")
    @PutMapping("/kiosk/settings")
    fun updateSettings(
        @PathVariable branchId: UUID,
        @RequestBody request: KioskSettings,
        @RequestHeader("Authorization", required = false) auth: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<KioskSettings> = noStore(settings.updateSettings(managedBranch(branchId, auth, jwt), request))

    private fun branch(branchId: UUID): MerchantBranch =
        branchRepository.findById(branchId).orElseThrow { NoSuchElementException("Branch not found: $branchId") }

    private fun managedBranch(branchId: UUID, auth: String?, jwt: Jwt?): MerchantBranch = branch(branchId).also { authorizeManager(it, auth, jwt) }

    private fun authorizeManager(branch: MerchantBranch, auth: String?, jwt: Jwt?) {
        if (PlatformAdminAccess.isPlatformAdmin(jwt)) return
        val isManager = runCatching { callerResolver.resolve(auth, branch.merchantId).isManagerFor(branch.merchantId) }
            .getOrDefault(false)
        if (!isManager) throw KioskAccessDeniedException("Caller is neither a Merchant Manager for this Branch's Merchant nor a platform admin")
    }

    /** The `groups` claim may carry plain names or full paths, so only the last path segment is compared. */
    private fun isBranchMember(jwt: Jwt?, branchId: UUID): Boolean {
        val expected = KeycloakGroupNames.branchGroup(branchId)
        return jwt?.getClaimAsStringList("groups").orEmpty().any { it.substringAfterLast('/').equals(expected, ignoreCase = true) }
    }

    private fun <T : Any> noStore(body: T): ResponseEntity<T> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body)
}
