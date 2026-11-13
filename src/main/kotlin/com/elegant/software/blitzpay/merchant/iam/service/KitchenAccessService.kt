package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.config.KitchenAccessProperties
import com.elegant.software.blitzpay.merchant.iam.web.KitchenAccessStatusResponse
import com.elegant.software.blitzpay.merchant.iam.web.KitchenLoginResponse
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/** The Kitchen Login of a Branch: one Keycloak user per Branch, provisioned by [BranchLoginProvisioner]. */
@Service
@Profile("!contract-test")
class KitchenAccessService(
    keycloak: Keycloak,
    private val keycloakProperties: KeycloakProperties,
    private val kitchenProperties: KitchenAccessProperties,
    groupSyncService: MerchantGroupSyncService,
    merchantRepository: MerchantRepository,
    passwordGenerator: KitchenPasswordGenerator,
) {
    private val logins = BranchLoginProvisioner(
        keycloak, keycloakProperties, kitchenProperties, "BlitzPay Kitchen Screen", "Kitchen Login",
        groupSyncService, merchantRepository, passwordGenerator, LoggerFactory.getLogger(KitchenAccessService::class.java),
    )

    fun enable(branch: MerchantBranch): KitchenLoginResponse {
        val login = logins.enable(branch, kitchenUsername(branch.branchCode), "Kitchen", branch.name)
        return KitchenLoginResponse(
            username = login.username,
            password = login.password,
            keycloakUrl = keycloakProperties.serverUrl,
            realm = keycloakProperties.realm,
            clientId = kitchenProperties.clientId,
            apiBaseUrl = kitchenProperties.apiBaseUrl,
            branchId = branch.id,
        )
    }

    /** Disables the kitchen user and revokes all its sessions, offline sessions included. */
    fun disable(branch: MerchantBranch): KitchenAccessStatusResponse =
        KitchenAccessStatusResponse(enabled = false, username = logins.disable(branch, kitchenUsername(branch.branchCode)))

    fun status(branch: MerchantBranch): KitchenAccessStatusResponse {
        val user = logins.find(kitchenUsername(branch.branchCode))
            ?: return KitchenAccessStatusResponse(enabled = false, username = null)
        return KitchenAccessStatusResponse(enabled = user.isEnabled == true, username = user.username)
    }

    companion object {
        /** Human-readable username from the Branch Code (ADR-0012), e.g. `BR-1A2B3C` → `kitchen-br-1a2b3c`. */
        fun kitchenUsername(branchCode: String): String = BranchLoginProvisioner.username("kitchen", branchCode)
    }
}
