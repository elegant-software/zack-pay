package com.elegant.software.blitzpay.payments.stripe.web

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.StripeClient
import com.stripe.param.v2.core.AccountCreateParams
import com.stripe.param.v2.core.AccountLinkCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StripeConnectService(
    private val stripeConnectGateway: StripeConnectGateway,
    private val stripeProperties: StripeProperties,
) {
    private val log = LoggerFactory.getLogger(StripeConnectService::class.java)
    private val stripeClient by lazy { StripeClient(stripeProperties.secretKey) }

    fun createOnboardingUrl(merchantId: UUID, returnUrl: String, refreshUrl: String): String {
        val existingAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
        val connectedAccountId = existingAccountId ?: run {
            val details = stripeConnectGateway.getDetails(merchantId)
            val account = stripeClient.v2().core().accounts().create(buildAccountCreateParams(details))
            stripeConnectGateway.save(merchantId, account.id)
            log.info("stripe_connect v2 account created merchantId={} connectedAccountId={}", merchantId, account.id)
            account.id
        }
        val url = createAccountLink(connectedAccountId, returnUrl, refreshUrl)
        log.info("stripe_connect onboarding link created merchantId={} connectedAccountId={}", merchantId, connectedAccountId)
        return url
    }

    fun createRefreshUrl(merchantId: UUID, returnUrl: String, refreshUrl: String): String {
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
            ?: throw NoSuchElementException("No Stripe Connect account found for merchant $merchantId")
        val url = createAccountLink(connectedAccountId, returnUrl, refreshUrl)
        log.info("stripe_connect refresh link created merchantId={} connectedAccountId={}", merchantId, connectedAccountId)
        return url
    }

    private fun createAccountLink(connectedAccountId: String, returnUrl: String, refreshUrl: String): String =
        stripeClient.v2().core().accountLinks()
            .create(buildAccountLinkCreateParams(connectedAccountId, returnUrl, refreshUrl))
            .url

    fun findConnectedAccountId(merchantId: UUID): String? = stripeConnectGateway.findConnectedAccountId(merchantId)
    fun isChargesEnabled(merchantId: UUID): Boolean = stripeConnectGateway.isChargesEnabled(merchantId)
    fun isDetailsSubmitted(merchantId: UUID): Boolean = stripeConnectGateway.isDetailsSubmitted(merchantId)
    fun isPayByBankEnabled(merchantId: UUID): Boolean = stripeConnectGateway.isPayByBankEnabled(merchantId)
    fun isTapToPayEnabled(merchantId: UUID): Boolean = stripeConnectGateway.isTapToPayEnabled(merchantId)
    fun enableTapToPay(merchantId: UUID) = stripeConnectGateway.enableTapToPay(merchantId)
    fun disableTapToPay(merchantId: UUID) = stripeConnectGateway.disableTapToPay(merchantId)
}

private val ISO_COUNTRY = Regex("[A-Za-z]{2}")

/**
 * Accounts v2 connected account for a merchant. The platform is not eligible to own loss liability, so
 * Stripe collects fees and losses (Stripe-managed risk); Stripe does not allow changing these
 * responsibilities after creation. That combination requires the full Stripe dashboard (Express with
 * Stripe-managed risk is preview-only). Payments are direct charges (`Stripe-Account` header), so only
 * the merchant configuration is needed. The recipient configuration (v1 `transfers`) is deliberately
 * omitted: nothing transfers funds to connected accounts, and Stripe rejects it without a contact email.
 */
internal fun buildAccountCreateParams(details: MerchantDetailsResponse): AccountCreateParams {
    val website = details.website?.takeIf { it.isNotBlank() }
    val displayName = details.merchantName.takeIf { it.isNotBlank() }

    val merchantConfig = AccountCreateParams.Configuration.Merchant.builder()
        .setCapabilities(
            AccountCreateParams.Configuration.Merchant.Capabilities.builder()
                .setCardPayments(
                    AccountCreateParams.Configuration.Merchant.Capabilities.CardPayments.builder()
                        .setRequested(true).build()
                )
                .setPayByBankPayments(
                    AccountCreateParams.Configuration.Merchant.Capabilities.PayByBankPayments.builder()
                        .setRequested(true).build()
                )
                .build()
        )
    website?.let {
        merchantConfig.setSupport(AccountCreateParams.Configuration.Merchant.Support.builder().setUrl(it).build())
    }

    val profile = AccountCreateParams.Defaults.Profile.builder()
    website?.let { profile.setBusinessUrl(it) }
    displayName?.let { profile.setDoingBusinessAs(it) }

    val params = AccountCreateParams.builder()
        .setDashboard(AccountCreateParams.Dashboard.FULL)
        .setConfiguration(
            AccountCreateParams.Configuration.builder()
                .setMerchant(merchantConfig.build())
                .build()
        )
        .setDefaults(
            AccountCreateParams.Defaults.builder()
                .setResponsibilities(
                    AccountCreateParams.Defaults.Responsibilities.builder()
                        .setFeesCollector(AccountCreateParams.Defaults.Responsibilities.FeesCollector.STRIPE)
                        .setLossesCollector(AccountCreateParams.Defaults.Responsibilities.LossesCollector.STRIPE)
                        .build()
                )
                .setProfile(profile.build())
                .build()
        )
        .putMetadata("merchantId", details.merchantId.toString())

    details.contactInfo.email?.takeIf { it.isNotBlank() }?.let { params.setContactEmail(it) }
    displayName?.let { params.setDisplayName(it) }
    details.operatingCountry.trim().takeIf { it.matches(ISO_COUNTRY) }?.let {
        params.setIdentity(AccountCreateParams.Identity.builder().setCountry(it.lowercase()).build())
    }
    return params.build()
}

internal fun buildAccountLinkCreateParams(
    connectedAccountId: String,
    returnUrl: String,
    refreshUrl: String,
): AccountLinkCreateParams =
    AccountLinkCreateParams.builder()
        .setAccount(connectedAccountId)
        .setUseCase(
            AccountLinkCreateParams.UseCase.builder()
                .setType(AccountLinkCreateParams.UseCase.Type.ACCOUNT_ONBOARDING)
                .setAccountOnboarding(
                    AccountLinkCreateParams.UseCase.AccountOnboarding.builder()
                        .setReturnUrl(returnUrl)
                        .setRefreshUrl(refreshUrl)
                        .build()
                )
                .build()
        )
        .build()
