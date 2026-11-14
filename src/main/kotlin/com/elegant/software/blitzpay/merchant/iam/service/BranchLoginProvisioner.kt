package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.iam.config.BranchLoginProperties
import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.ProtocolMapperRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.Logger
import org.springframework.http.HttpStatus
import java.text.Normalizer

/** Thrown when an admin-chosen device login password is unusable. */
class BranchLoginPasswordException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message, "INVALID_PASSWORD")

/** Thrown when a device login username is already taken by a user bound to another Branch. */
class BranchLoginConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)

/** A device login just provisioned: the username Keycloak stored and the password, which exists only here. */
data class ProvisionedLogin(val username: String, val password: String) {
    override fun toString(): String = "ProvisionedLogin(username=$username, password=****)"
}

/**
 * Provisions a shared Branch device login in Keycloak: the device's realm client, the Branch's `branch_<branchId>`
 * group and a user in that group. Used for the Kitchen Login (one user per Branch) and Kiosk Logins (one user per
 * Kiosk), so both work the same way. Every step is idempotent.
 *
 * The password, chosen by the admin or generated, is returned to the caller only. It is never stored and never logged.
 */
class BranchLoginProvisioner(
    private val keycloak: Keycloak,
    private val keycloakProperties: KeycloakProperties,
    private val properties: BranchLoginProperties,
    /** Display name of the realm client, e.g. `BlitzPay Kitchen Screen`. */
    private val clientName: String,
    /** Name of the login in messages and logs, e.g. `Kitchen Login`. */
    private val loginName: String,
    private val groupSyncService: MerchantGroupSyncService,
    private val merchantRepository: MerchantRepository,
    private val passwordGenerator: KitchenPasswordGenerator,
    private val log: Logger,
) {
    /**
     * Creates or re-enables the user, puts it in the Branch group and sets [password], or a generated one when null.
     */
    fun enable(branch: MerchantBranch, username: String, firstName: String, lastName: String, password: String? = null): ProvisionedLogin {
        if (password != null && password.length !in PASSWORD_LENGTH) {
            throw BranchLoginPasswordException("Password must be ${PASSWORD_LENGTH.first} to ${PASSWORD_LENGTH.last} characters")
        }
        ensureClient()

        val merchantName = merchantRepository.findById(branch.merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: ${branch.merchantId}") }
            .merchantName
        val branchGroupId = groupSyncService.ensureBranchGroupExists(branch.merchantId, merchantName, branch.id, branch.name)

        val userId = ensureUser(username, branch, firstName, lastName)
        val user = realm().users().get(userId)
        // The username Keycloak stored: the email in realms with "Email as username".
        val storedUsername = user.toRepresentation().username ?: username
        user.joinGroup(branchGroupId)

        val chosen = password ?: passwordGenerator.generate()
        try {
            user.resetPassword(CredentialRepresentation().apply {
                type = CredentialRepresentation.PASSWORD
                value = chosen
                isTemporary = false
            })
        } catch (e: BadRequestException) {
            // The realm's password policy refused it; only an admin-chosen password can realistically hit this.
            throw BranchLoginPasswordException("Keycloak's password policy refused the password")
        }
        log.info("{} provisioned branchId={} username={}", loginName, branch.id, storedUsername)
        return ProvisionedLogin(storedUsername, chosen)
    }

    /**
     * Disables the user and revokes all its sessions, offline sessions included. Returns the stored username, or
     * null when the user doesn't exist.
     */
    fun disable(branch: MerchantBranch, username: String): String? {
        val existing = findUser(username) ?: return null
        val user = realm().users().get(existing.id)

        user.update(user.toRepresentation().apply { isEnabled = false })
        // Removes the user's online and offline sessions.
        user.logout()
        // Also revokes the offline token granted to the device client; 404 means there was none.
        try {
            user.revokeConsent(properties.clientId)
        } catch (_: NotFoundException) {
        }
        log.info("{} disabled branchId={} username={}", loginName, branch.id, username)
        return existing.username
    }

    /** Deletes the user, which ends all its sessions. Does nothing when the user doesn't exist. */
    fun delete(branch: MerchantBranch, username: String) {
        val existing = findUser(username) ?: return
        realm().users().delete(existing.id).close()
        log.info("{} deleted branchId={} username={}", loginName, branch.id, username)
    }

    /** The user as Keycloak has it, or null when it was never provisioned. */
    fun find(username: String): UserRepresentation? = findUser(username)

    // ── realm client ────────────────────────────────────────────────────────

    /**
     * Creates and configures the client once. An existing client is left untouched, so routine enables need no
     * client write access; never deleted either: that would end every device's offline session.
     */
    private fun ensureClient() {
        if (findClient() != null) return
        // ponytail: config drift on an existing client is not repaired; fix it in Keycloak by hand
        val internalId = createClient(applyClientConfig(ClientRepresentation())) ?: return
        val client = realm().clients().get(internalId)
        ensureGroupsMapper(client)
        ensureOfflineAccessOptional(client)
        log.info("keycloak {} client created clientId={}", loginName, properties.clientId)
    }

    private fun findClient(): ClientRepresentation? =
        realm().clients().findByClientId(properties.clientId).firstOrNull { it.clientId == properties.clientId }

    /** Returns the new client's id, or null if it was created concurrently (that caller configures it). */
    private fun createClient(rep: ClientRepresentation): String? {
        val (status, location) = realm().clients().create(rep).use { it.status to it.getHeaderString("Location") }
        if (status == Response.Status.CONFLICT.statusCode) return null
        check(status in 200..299) { "Keycloak $loginName client creation failed with HTTP $status" }
        return location?.substringAfterLast("/")
            ?: throw IllegalStateException("No Location header in Keycloak client creation response")
    }

    private fun applyClientConfig(rep: ClientRepresentation): ClientRepresentation = rep.apply {
        clientId = properties.clientId
        name = rep.name ?: clientName
        protocol = OIDC
        isEnabled = true
        isPublicClient = true
        isBearerOnly = false
        isConsentRequired = false
        isStandardFlowEnabled = true
        isImplicitFlowEnabled = false
        isDirectAccessGrantsEnabled = false
        isServiceAccountsEnabled = false
        redirectUris = listOf(properties.redirectUri)
        webOrigins = listOf("+")
        attributes = (rep.attributes ?: emptyMap()) + mapOf(
            "pkce.code.challenge.method" to "S256",
            "post.logout.redirect.uris" to properties.redirectUri,
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

    // ── device user ─────────────────────────────────────────────────────────

    private fun ensureUser(username: String, branch: MerchantBranch, firstName: String, lastName: String): String {
        val users = realm().users()
        val existing = findUser(username)
        if (existing == null) {
            val (status, location) = users.create(userRep(UserRepresentation(), username, firstName, lastName))
                .use { it.status to it.getHeaderString("Location") }
            if (status != Response.Status.CONFLICT.statusCode) {
                check(status in 200..299) { "Keycloak $loginName user creation failed with HTTP $status" }
                return location?.substringAfterLast("/")
                    ?: throw IllegalStateException("No Location header in Keycloak user creation response")
            }
        }
        val user = existing ?: findUser(username)
            ?: throw IllegalStateException("$loginName user not found after 409: $username")
        val resource = users.get(user.id)

        val expectedGroup = KeycloakGroupNames.branchGroup(branch.id)
        val otherBranch = resource.groups().firstOrNull {
            val name = it.name.orEmpty()
            name.startsWith(KeycloakGroupNames.BRANCH_PREFIX) && !name.equals(expectedGroup, ignoreCase = true)
        }
        if (otherBranch != null) {
            throw BranchLoginConflictException("Username $username is already used by another Branch's $loginName")
        }

        resource.update(userRep(resource.toRepresentation(), username, firstName, lastName))
        return user.id
    }

    private fun userRep(rep: UserRepresentation, username: String, firstName: String, lastName: String) = rep.apply {
        this.username = username
        isEnabled = true
        requiredActions = emptyList()
        this.firstName = firstName
        this.lastName = lastName
        email = placeholderEmail(username)
        isEmailVerified = true
    }

    /** By username, else by the placeholder email: realms with "Email as username" store the email as username. */
    private fun findUser(username: String): UserRepresentation? {
        val users = realm().users()
        val email = placeholderEmail(username)
        return users.searchByUsername(username, true).firstOrNull { it.username.equals(username, ignoreCase = true) }
            ?: users.searchByEmail(email, true).firstOrNull { it.email.equals(email, ignoreCase = true) || it.username.equals(email, ignoreCase = true) }
    }

    private fun placeholderEmail(username: String) = "$username@${properties.emailDomain}"

    private fun realm(): RealmResource = keycloak.realm(keycloakProperties.realm)

    companion object {
        private const val OIDC = "openid-connect"
        private const val GROUPS_CLAIM = "groups"
        private const val GROUP_MEMBERSHIP_MAPPER = "oidc-group-membership-mapper"
        private const val OFFLINE_ACCESS = "offline_access"
        private val PASSWORD_LENGTH = 8..128
        private val NON_USERNAME_CHARS = Regex("[^a-z0-9._-]+")
        private val COMBINING_MARKS = Regex("\\p{M}+")

        /** Lower-case, ASCII, dash-separated form of a name for a username, e.g. `Café Köln!` → `cafe-koln`. */
        fun slug(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "")
            .lowercase().replace(NON_USERNAME_CHARS, "-").trim('-')

        /**
         * Readable username from the Branch name and a device name, e.g. `Main St` + `Kitchen` → `main-st-kitchen`. The
         * Branch Code stands in for a Branch name with no usable characters. No UUID in it (ADR-0012).
         */
        fun username(branchName: String, branchCode: String, deviceName: String): String {
            val branch = slug(branchName).ifEmpty { slug(branchCode) }
            val device = slug(deviceName).ifEmpty { "device" }
            return "$branch-$device".take(MAX_USERNAME_LENGTH).trimEnd('-')
        }

        /** Fits the `merchant_kiosks.username` column. */
        private const val MAX_USERNAME_LENGTH = 128
    }
}
