package com.elegant.software.blitzpay.payments.stripe.web

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.model.Account
import com.stripe.model.AccountLink
import com.stripe.net.RequestOptions
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StripeConnectService(
    private val stripeConnectGateway: StripeConnectGateway,
    private val stripeProperties: StripeProperties,
) {
    private val log = LoggerFactory.getLogger(StripeConnectService::class.java)

    fun createOnboardingUrl(merchantId: UUID, returnUrl: String, refreshUrl: String): String {
        val requestOptions = RequestOptions.builder().setApiKey(stripeProperties.secretKey).build()
        val existingAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
        val connectedAccountId = existingAccountId ?: run {
            val details = stripeConnectGateway.getDetails(merchantId)
            val account = Account.create(buildAccountCreateParams(details), requestOptions)
            stripeConnectGateway.save(merchantId, account.id)
            log.info("stripe_connect express account created merchantId={} connectedAccountId={}", merchantId, account.id)
            account.id
        }
        val link = AccountLink.create(
            AccountLinkCreateParams.builder()
                .setAccount(connectedAccountId)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .setReturnUrl(returnUrl)
                .setRefreshUrl(refreshUrl)
                .build(),
            requestOptions,
        )
        log.info("stripe_connect onboarding link created merchantId={} connectedAccountId={}", merchantId, connectedAccountId)
        return link.url
    }

    fun createRefreshUrl(merchantId: UUID, returnUrl: String, refreshUrl: String): String {
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
            ?: throw NoSuchElementException("No Stripe Connect account found for merchant $merchantId")
        val requestOptions = RequestOptions.builder().setApiKey(stripeProperties.secretKey).build()
        val link = AccountLink.create(
            AccountLinkCreateParams.builder()
                .setAccount(connectedAccountId)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .setReturnUrl(returnUrl)
                .setRefreshUrl(refreshUrl)
                .build(),
            requestOptions,
        )
        log.info("stripe_connect refresh link created merchantId={} connectedAccountId={}", merchantId, connectedAccountId)
        return link.url
    }

    private fun buildAccountCreateParams(details: MerchantDetailsResponse): AccountCreateParams {
        val params = AccountCreateParams.builder()
            .setType(AccountCreateParams.Type.EXPRESS)
            .setEmail(details.contactInfo.email)
            .setCapabilities(
                AccountCreateParams.Capabilities.builder()
                    .setCardPayments(
                        AccountCreateParams.Capabilities.CardPayments.builder().setRequested(true).build()
                    )
                    .setTransfers(
                        AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build()
                    )
                    .build()
            )
            .putExtraParam("capabilities[pay_by_bank_payments][requested]", true)

        val bizProfile = AccountCreateParams.BusinessProfile.builder()
        if (details.merchantName.isNotBlank()) bizProfile.setName(details.merchantName)
        details.website?.takeIf { it.isNotBlank() }?.let { bizProfile.setUrl(it) }
        params.setBusinessProfile(bizProfile.build())
        return params.build()
    }

    fun findConnectedAccountId(merchantId: UUID): String? = stripeConnectGateway.findConnectedAccountId(merchantId)
    fun isChargesEnabled(merchantId: UUID): Boolean = stripeConnectGateway.isChargesEnabled(merchantId)
    fun isDetailsSubmitted(merchantId: UUID): Boolean = stripeConnectGateway.isDetailsSubmitted(merchantId)
    fun isPayByBankEnabled(merchantId: UUID): Boolean = stripeConnectGateway.isPayByBankEnabled(merchantId)
    fun isTapToPayEnabled(merchantId: UUID): Boolean = stripeConnectGateway.isTapToPayEnabled(merchantId)
    fun enableTapToPay(merchantId: UUID) = stripeConnectGateway.enableTapToPay(merchantId)
    fun disableTapToPay(merchantId: UUID) = stripeConnectGateway.disableTapToPay(merchantId)
}
