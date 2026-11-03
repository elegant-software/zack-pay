package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.config.KitchenAccessProperties
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.web.KitchenAccessStatusResponse
import com.elegant.software.blitzpay.merchant.iam.web.KitchenLoginResponse
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.ProtocolMapperRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/** Thrown when the Kitchen Login username is already taken by a user bound to another Branch. */
class KitchenLoginConflictException(message: String) : RuntimeException(message)

/**
 * Provisions Kitchen Screen access for a Branch in Keycloak: the shared kitchen realm client, the Branch's
 * `branch_<branchId>` group and one kitchen user per Branch. Every step is idempotent.
 *
 * The generated password is returned to the caller only. It is never stored and never logged.
 */
@Service
@Profile("!contract-test")
class KitchenAccessService(
    private val keycloak: Keycloak,
    private val keycloakProperties: KeycloakProperties,
    private val kitchenProperties: KitchenAccessProperties,
    private val groupSyncService: MerchantGroupSyncService,
    private val merchantRepository: MerchantRepository,
    private val passwordGenerator: KitchenPasswordGenerator,
) {
    private val log = LoggerFactory.getLogger(KitchenAccessService::class.java)

    fun enable(branch: MerchantBranch): KitchenLoginResponse {
        ensureKitchenClient()

        val merchantName = merchantRepository.findById(branch.merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: ${branch.merchantId}") }
            .merchantName
        val branchGroupId = groupSyncService.ensureBranchGroupExists(branch.merchantId, merchantName, branch.id, branch.name)

        val username = kitchenUsername(branch.branchCode)
        val userId = ensureKitchenUser(username, branch)
        val user = realm().users().get(userId)
        user.joinGroup(branchGroupId)

        val password = passwordGenerator.generate()
        user.resetPassword(CredentialRepresentation().apply {
            type = CredentialRepresentation.PASSWORD
            value = password
            isTemporary = false
        })
        log.info("kitchen login provisioned branchId={} username={}", branch.id, username)

        return KitchenLoginResponse(
            username = username,
            password = password,
            keycloakUrl = keycloakProperties.serverUrl,
            realm = keycloakProperties.realm,
            clientId = kitchenProperties.clientId,
            apiBaseUrl = kitchenProperties.apiBaseUrl,
            branchId = branch.id,
        )
    }

    /** Disables the kitchen user and revokes all its sessions, offline sessions included. */
    fun disable(branch: MerchantBranch): KitchenAccessStatusResponse {
        val username = kitchenUsername(branch.branchCode)
        val existing = findUser(username) ?: return KitchenAccessStatusResponse(enabled = false, username = null)
        val user = realm().users().get(existing.id)

        user.update(user.toRepresentation().apply { isEnabled = false })
        // Removes the user's online and offline sessions.
        user.logout()
        // Also revokes the offline token granted to the kitchen client; 404 means there was none.
        try {
            user.revokeConsent(kitchenProperties.clientId)
        } catch (_: NotFoundException) {
        }
        log.info("kitchen login disabled branchId={} username={}", branch.id, username)
        return KitchenAccessStatusResponse(enabled = false, username = username)
    }

    fun status(branch: MerchantBranch): KitchenAccessStatusResponse {
        val user = findUser(kitchenUsername(branch.branchCode))
            ?: return KitchenAccessStatusResponse(enabled = false, username = null)
        return KitchenAccessStatusResponse(enabled = user.isEnabled == true, username = user.username)
    }

    // ── realm client ────────────────────────────────────────────────────────

    /** Creates the kitchen client, or overwrites the existing one in place. Never deletes it: that would end every kitchen offline session. */
    private fun ensureKitchenClient() {
        val clients = realm().clients()
        val existing = findClient()
        val internalId = if (existing == null) {
            createClient(applyKitchenClientConfig(ClientRepresentation()))
        } else {
            clients.get(existing.id).update(applyKitchenClientConfig(existing))
            existing.id
        }
        val client = clients.get(internalId)
        ensureGroupsMapper(client)
        ensureOfflineAccessOptional(client)
        log.info("keycloak kitchen client ensured clientId={} created={}", kitchenProperties.clientId, existing == null)
    }

    private fun findClient(): ClientRepresentation? =
        realm().clients().findByClientId(kitchenProperties.clientId).firstOrNull { it.clientId == kitchenProperties.clientId }

    private fun createClient(rep: ClientRepresentation): String {
        val (status, location) = realm().clients().create(rep).use { it.status to it.getHeaderString("Location") }
        if (status == Response.Status.CONFLICT.statusCode) {
            // Created concurrently: overwrite that one instead.
            val raced = findClient() ?: throw IllegalStateException("kitchen client not found after 409: ${kitchenProperties.clientId}")
            realm().clients().get(raced.id).update(applyKitchenClientConfig(raced))
            return raced.id
        }
        check(status in 200..299) { "Keycloak kitchen client creation failed with HTTP $status" }
        return location?.substringAfterLast("/")
            ?: throw IllegalStateException("No Location header in Keycloak client creation response")
    }

    private fun applyKitchenClientConfig(rep: ClientRepresentation): ClientRepresentation = rep.apply {
        clientId = kitchenProperties.clientId
        name = rep.name ?: "BlitzPay Kitchen Screen"
        protocol = OIDC
        isEnabled = true
        isPublicClient = true
        isBearerOnly = false
        isConsentRequired = false
        isStandardFlowEnabled = true
        isImplicitFlowEnabled = false
        isDirectAccessGrantsEnabled = false
        isServiceAccountsEnabled = false
        redirectUris = listOf(kitchenProperties.redirectUri)
        webOrigins = listOf("+")
        attributes = (rep.attributes ?: emptyMap()) + mapOf(
            "pkce.code.challenge.method" to "S256",
            "post.logout.redirect.uris" to kitchenProperties.redirectUri,
        )
        // Mappers and client scopes are managed through their own endpoints below, not through this PUT.
        protocolMappers = null
        defaultClientScopes = null
        optionalClientScopes = null
    }

    private fun ensureGroupsMapper(client: ClientResource) {
        val mappers = client.protocolMappers
        val candidates = mappers.mappers.filter {
            it.name == GROUPS_CLAIM ||
                (it.protocolMapper == GROUP_MEMBERSHIP_MAPPER && it.config?.get("claim.name") == GROUPS_CLAIM)
        }
        val keep = candidates.firstOrNull { it.name == GROUPS_CLAIM && it.protocolMapper == GROUP_MEMBERSHIP_MAPPER }
            ?: candidates.firstOrNull { it.protocolMapper == GROUP_MEMBERSHIP_MAPPER }

        // Remove every other mapper that would emit or clash with the `groups` claim, so the one kept can take its name.
        candidates.filter { it.id != keep?.id }.forEach { mappers.delete(it.id) }

        if (keep != null) {
            mappers.update(keep.id, groupsMapper().apply { id = keep.id })
        } else {
            mappers.createMapper(groupsMapper()).use { response ->
                check(response.status in 200..299) { "Keycloak groups mapper creation failed with HTTP ${response.status}" }
            }
        }
    }

    private fun groupsMapper() = ProtocolMapperRepresentation().apply {
        name = GROUPS_CLAIM
        protocol = OIDC
        protocolMapper = GROUP_MEMBERSHIP_MAPPER
        config = mapOf(
            "claim.name" to GROUPS_CLAIM,
            "full.path" to "false",
            "access.token.claim" to "true",
            "id.token.claim" to "true",
            "userinfo.token.claim" to "true",
            "introspection.token.claim" to "true",
        )
    }

    private fun ensureOfflineAccessOptional(client: ClientResource) {
        val scopeId = realm().clientScopes().findAll().firstOrNull { it.name == OFFLINE_ACCESS }?.id
            ?: throw IllegalStateException("Keycloak realm has no '$OFFLINE_ACCESS' client scope")
        if (client.optionalClientScopes.any { it.id == scopeId }) return
        if (client.defaultClientScopes.any { it.id == scopeId }) client.removeDefaultClientScope(scopeId)
        client.addOptionalClientScope(scopeId)
    }

    // ── kitchen user ────────────────────────────────────────────────────────

    private fun ensureKitchenUser(username: String, branch: MerchantBranch): String {
        val users = realm().users()
        val existing = findUser(username)
        if (existing == null) {
            val (status, location) = users.create(kitchenUser(UserRepresentation(), username, branch))
                .use { it.status to it.getHeaderString("Location") }
            if (status != Response.Status.CONFLICT.statusCode) {
                check(status in 200..299) { "Keycloak kitchen user creation failed with HTTP $status" }
                return location?.substringAfterLast("/")
                    ?: throw IllegalStateException("No Location header in Keycloak user creation response")
            }
        }
        val user = existing ?: findUser(username)
            ?: throw IllegalStateException("kitchen user not found after 409: $username")
        val resource = users.get(user.id)

        val expectedGroup = KeycloakGroupNames.branchGroup(branch.id)
        val otherBranch = resource.groups().firstOrNull {
            val name = it.name.orEmpty()
            name.startsWith(KeycloakGroupNames.BRANCH_PREFIX) && !name.equals(expectedGroup, ignoreCase = true)
        }
        if (otherBranch != null) {
            throw KitchenLoginConflictException("Username $username is already used by another Branch's Kitchen Login")
        }

        resource.update(kitchenUser(resource.toRepresentation(), username, branch))
        return user.id
    }

    private fun kitchenUser(rep: UserRepresentation, username: String, branch: MerchantBranch) = rep.apply {
        this.username = username
        isEnabled = true
        requiredActions = emptyList()
        firstName = "Kitchen"
        lastName = branch.name
        email = "$username@${kitchenProperties.emailDomain}"
        isEmailVerified = true
    }

    private fun findUser(username: String): UserRepresentation? =
        realm().users().searchByUsername(username, true).firstOrNull { it.username.equals(username, ignoreCase = true) }

    private fun realm(): RealmResource = keycloak.realm(keycloakProperties.realm)

    companion object {
        private const val OIDC = "openid-connect"
        private const val GROUPS_CLAIM = "groups"
        private const val GROUP_MEMBERSHIP_MAPPER = "oidc-group-membership-mapper"
        private const val OFFLINE_ACCESS = "offline_access"
        private val NON_USERNAME_CHARS = Regex("[^a-z0-9._-]+")

        /** Human-readable username from the Branch Code (ADR-0012), e.g. `BR-1A2B3C` → `kitchen-br-1a2b3c`. */
        fun kitchenUsername(branchCode: String): String =
            "kitchen-" + branchCode.lowercase().replace(NON_USERNAME_CHARS, "-").trim('-')
    }
}
