package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.config.KioskAccessProperties
import com.elegant.software.blitzpay.merchant.iam.web.KioskLoginResponse
import com.elegant.software.blitzpay.merchant.iam.web.KioskResponse
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKiosk
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The Kiosks of a Branch. Each Kiosk has its own Kiosk Login: a Keycloak user in the Branch group, provisioned exactly
 * like the Kitchen Login by [BranchLoginProvisioner] when the Kiosk is added in the web admin.
 */
@Service
@Profile("!contract-test")
class KioskAccessService(
    private val kiosks: MerchantKioskRepository,
    keycloak: Keycloak,
    private val keycloakProperties: KeycloakProperties,
    private val kioskProperties: KioskAccessProperties,
    groupSyncService: MerchantGroupSyncService,
    merchantRepository: MerchantRepository,
    passwordGenerator: KitchenPasswordGenerator,
) {
    private val logins = BranchLoginProvisioner(
        keycloak, keycloakProperties, kioskProperties, "BlitzPay Kiosk", "Kiosk Login",
        groupSyncService, merchantRepository, passwordGenerator, LoggerFactory.getLogger(KioskAccessService::class.java),
    )

    // ponytail: one Keycloak lookup per Kiosk; fine for the handful a Branch has
    @Transactional(readOnly = true)
    fun list(branch: MerchantBranch): List<KioskResponse> =
        kiosks.findAllByBranchIdOrderByNumber(branch.id).map { it.toResponse(logins.find(it.username, branch)?.isEnabled == true) }

    /** Adds a Kiosk and provisions its Kiosk Login with the admin's [password], or a generated one; returned once. */
    @Transactional
    fun create(branch: MerchantBranch, name: String?, password: String? = null): KioskLoginResponse {
        // ponytail: two concurrent adds may pick the same number; the unique index rejects the second
        val number = (kiosks.findTopByBranchIdOrderByNumberDesc(branch.id)?.number ?: 0) + 1
        val kioskName = name?.trim()?.takeIf { it.isNotEmpty() }?.take(100) ?: "Kiosk $number"
        val readable = kioskUsername(branch, kioskName)
        // Taken by another Kiosk, by this Branch's Kitchen Login (Kiosk named "Kitchen"), or by any other user of the
        // business realm (same Branch name at another Merchant): keep it unique with the number.
        val taken = kiosks.existsByUsername(readable) || readable == KitchenAccessService.kitchenUsername(branch) || logins.exists(readable)
        val username = if (taken) "$readable-$number" else readable
        val kiosk = kiosks.save(MerchantKiosk(branchId = branch.id, number = number, name = kioskName, username = username))
        return provision(branch, kiosk, password)
    }

    /** Enables the Kiosk's login again, or resets it: the admin's [password] or a generated one, shown once. */
    @Transactional(readOnly = true)
    fun enable(branch: MerchantBranch, kioskId: UUID, password: String? = null): KioskLoginResponse =
        provision(branch, kiosk(branch, kioskId), password)

    /** Disables the Kiosk's login and revokes all its sessions, offline sessions included. */
    @Transactional(readOnly = true)
    fun disable(branch: MerchantBranch, kioskId: UUID): KioskResponse {
        val kiosk = kiosk(branch, kioskId)
        logins.disable(branch, kiosk.username)
        return kiosk.toResponse(enabled = false)
    }

    /** Removes the Kiosk and its Keycloak user, which ends its sessions. */
    @Transactional
    fun delete(branch: MerchantBranch, kioskId: UUID) {
        val kiosk = kiosk(branch, kioskId)
        logins.delete(branch, kiosk.username)
        kiosks.delete(kiosk)
    }

    private fun provision(branch: MerchantBranch, kiosk: MerchantKiosk, password: String?): KioskLoginResponse {
        val login = logins.enable(branch, kiosk.username, kiosk.name, branch.name, password)
        return KioskLoginResponse(
            kioskId = kiosk.id,
            name = kiosk.name,
            username = login.username,
            password = login.password,
            keycloakUrl = keycloakProperties.serverUrl,
            realm = keycloakProperties.realm,
            clientId = kioskProperties.clientId,
            apiBaseUrl = kioskProperties.apiBaseUrl,
            branchId = branch.id,
        )
    }

    private fun kiosk(branch: MerchantBranch, kioskId: UUID): MerchantKiosk =
        kiosks.findByIdAndBranchId(kioskId, branch.id) ?: throw NoSuchElementException("Kiosk not found: $kioskId")

    private fun MerchantKiosk.toResponse(enabled: Boolean) = KioskResponse(id, number, name, username, enabled)

    companion object {
        /** E.g. Branch `Main St`, Kiosk `Entrance` → `main-st-entrance`. Fixed when the Kiosk is added. */
        fun kioskUsername(branch: MerchantBranch, kioskName: String): String =
            BranchLoginProvisioner.username(branch.name, branch.branchCode, kioskName)
    }
}
