package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.stripe.model.Account
import com.stripe.model.AccountLink
import com.stripe.net.RequestOptions
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

@Service
class StripeConnectService(
    private val stripeConnectGateway: StripeConnectGateway,
    private val stripeProperties: com.elegant.software.blitzpay.payments.stripe.config.StripeProperties,
) {
    private val log = LoggerFactory.getLogger(StripeConnectService::class.java)

    fun createOnboardingUrl(merchantId: UUID, returnUrl: String, refreshUrl: String): Mono<String> =
        Mono.fromCallable {
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
            link.url
        }.subscribeOn(Schedulers.boundedElastic())

    private fun buildAccountCreateParams(details: MerchantDetailsResponse): AccountCreateParams {
        val params = AccountCreateParams.builder()
            .setType(AccountCreateParams.Type.EXPRESS)
            .setEmail(details.contactEmail)
            .setCapabilities(
                AccountCreateParams.Capabilities.builder()
                    .setCardPayments(
                        AccountCreateParams.Capabilities.CardPayments.builder()
                            .setRequested(true).build()
                    )
                    .setTransfers(
                        AccountCreateParams.Capabilities.Transfers.builder()
                            .setRequested(true).build()
                    )
                    .build()
            )
            .putExtraParam("capabilities[pay_by_bank_payments][requested]", true)

        // Only pre-fill fields that don't trigger document verification.
        // Company/individual identity blocks are left for the merchant to complete
        // in the hosted onboarding form, since Stripe requires those to match
        // submitted documents exactly.
        val bizProfile = AccountCreateParams.BusinessProfile.builder()
        if (details.merchantName.isNotBlank()) bizProfile.setName(details.merchantName)
        details.website?.takeIf { it.isNotBlank() }?.let { bizProfile.setUrl(it) }
        params.setBusinessProfile(bizProfile.build())

        return params.build()
    }

    fun findConnectedAccountId(merchantId: UUID): String? =
        stripeConnectGateway.findConnectedAccountId(merchantId)

    fun isChargesEnabled(merchantId: UUID): Boolean =
        stripeConnectGateway.isChargesEnabled(merchantId)

    fun isDetailsSubmitted(merchantId: UUID): Boolean =
        stripeConnectGateway.isDetailsSubmitted(merchantId)

    fun isPayByBankEnabled(merchantId: UUID): Boolean =
        stripeConnectGateway.isPayByBankEnabled(merchantId)

    fun createRefreshUrl(merchantId: UUID, returnUrl: String, refreshUrl: String): Mono<String> =
        Mono.fromCallable {
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
            link.url
        }.subscribeOn(Schedulers.boundedElastic())
}
