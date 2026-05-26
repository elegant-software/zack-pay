package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.merchant.domain.MerchantProviderAccount
import com.elegant.software.blitzpay.merchant.domain.MerchantStripeProviderAccount
import com.elegant.software.blitzpay.merchant.repository.MerchantProviderAccountRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantStripeProviderAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
@Profile("!contract-test")
class StripeConnectGatewayImpl(
    private val providerAccountRepository: MerchantProviderAccountRepository,
    private val stripeProviderAccountRepository: MerchantStripeProviderAccountRepository,
    private val managementService: MerchantManagementService,
) : StripeConnectGateway {

    private val log = LoggerFactory.getLogger(StripeConnectGatewayImpl::class.java)

    override fun save(merchantId: UUID, connectedAccountId: String) {
        val providerAccount = providerAccountRepository
            .findByMerchantApplicationIdAndPspCode(merchantId, "STRIPE")
            ?: providerAccountRepository.save(MerchantProviderAccount(
                merchantApplicationId = merchantId,
                pspCode = "STRIPE",
                enabled = true,
            ))

        if (stripeProviderAccountRepository.existsById(providerAccount.id)) {
            log.warn(
                "stripe_connect save skipped — record already exists merchantId={} connectedAccountId={}",
                merchantId, connectedAccountId
            )
            return
        }
        stripeProviderAccountRepository.save(
            MerchantStripeProviderAccount(
                merchantProviderAccountId = providerAccount.id,
                connectedAccountId = connectedAccountId,
            )
        )
        log.info("stripe_connect saved merchantId={} connectedAccountId={}", merchantId, connectedAccountId)
    }

    override fun onAccountUpdated(
        connectedAccountId: String,
        chargesEnabled: Boolean,
        detailsSubmitted: Boolean,
        payByBankEnabled: Boolean,
    ) {
        val record = stripeProviderAccountRepository.findByConnectedAccountId(connectedAccountId) ?: run {
            log.warn(
                "stripe_connect account.updated ignored — no record found for connectedAccountId={}",
                connectedAccountId
            )
            return
        }
        record.chargesEnabled = chargesEnabled
        record.detailsSubmitted = detailsSubmitted
        record.payByBankEnabled = payByBankEnabled
        record.updatedAt = Instant.now()
        stripeProviderAccountRepository.save(record)
        log.info(
            "stripe_connect account.updated connectedAccountId={} chargesEnabled={} detailsSubmitted={} payByBankEnabled={}",
            connectedAccountId, chargesEnabled, detailsSubmitted, payByBankEnabled,
        )
    }

    override fun findConnectedAccountId(merchantId: UUID): String? =
        findStripeProviderAccount(merchantId)?.connectedAccountId

    override fun isChargesEnabled(merchantId: UUID): Boolean =
        findStripeProviderAccount(merchantId)?.chargesEnabled ?: false

    override fun isDetailsSubmitted(merchantId: UUID): Boolean =
        findStripeProviderAccount(merchantId)?.detailsSubmitted ?: false

    override fun isPayByBankEnabled(merchantId: UUID): Boolean =
        findStripeProviderAccount(merchantId)?.payByBankEnabled ?: false

    override fun getDetails(merchantId: UUID): MerchantDetailsResponse =
        managementService.get(merchantId)

    private fun findStripeProviderAccount(merchantId: UUID): MerchantStripeProviderAccount? {
        val providerAccount = providerAccountRepository
            .findByMerchantApplicationIdAndPspCode(merchantId, "STRIPE") ?: return null
        return stripeProviderAccountRepository.findById(providerAccount.id).orElse(null)
    }
}
