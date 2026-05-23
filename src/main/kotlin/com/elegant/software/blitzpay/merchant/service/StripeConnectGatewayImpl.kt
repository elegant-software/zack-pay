package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.merchant.domain.MerchantStripeConnect
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantStripeConnectRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
@Profile("!contract-test")
class StripeConnectGatewayImpl(
    private val repository: MerchantStripeConnectRepository,
    private val managementService: MerchantManagementService,
) : StripeConnectGateway {

    private val log = LoggerFactory.getLogger(StripeConnectGatewayImpl::class.java)

    override fun save(merchantId: UUID, connectedAccountId: String) {
        if (repository.existsById(merchantId)) {
            log.warn("stripe_connect save skipped — record already exists merchantId={} connectedAccountId={}", merchantId, connectedAccountId)
            return
        }
        repository.save(MerchantStripeConnect(merchantId = merchantId, connectedAccountId = connectedAccountId))
        log.info("stripe_connect saved merchantId={} connectedAccountId={}", merchantId, connectedAccountId)
    }

    override fun onAccountUpdated(
        connectedAccountId: String,
        chargesEnabled: Boolean,
        detailsSubmitted: Boolean,
        payByBankEnabled: Boolean,
    ) {
        val record = repository.findByConnectedAccountId(connectedAccountId) ?: run {
            log.warn("stripe_connect account.updated ignored — no record found for connectedAccountId={}", connectedAccountId)
            return
        }
        record.chargesEnabled = chargesEnabled
        record.detailsSubmitted = detailsSubmitted
        record.payByBankEnabled = payByBankEnabled
        record.updatedAt = Instant.now()
        repository.save(record)
        log.info(
            "stripe_connect account.updated merchantId={} connectedAccountId={} chargesEnabled={} detailsSubmitted={} payByBankEnabled={}",
            record.merchantId, connectedAccountId, chargesEnabled, detailsSubmitted, payByBankEnabled,
        )
    }

    override fun findConnectedAccountId(merchantId: UUID): String? =
        repository.findById(merchantId).orElse(null)?.connectedAccountId

    override fun isChargesEnabled(merchantId: UUID): Boolean =
        repository.findById(merchantId).orElse(null)?.chargesEnabled ?: false

    override fun isDetailsSubmitted(merchantId: UUID): Boolean =
        repository.findById(merchantId).orElse(null)?.detailsSubmitted ?: false

    override fun isPayByBankEnabled(merchantId: UUID): Boolean =
        repository.findById(merchantId).orElse(null)?.payByBankEnabled ?: false

    override fun getDetails(merchantId: UUID): MerchantDetailsResponse =
        managementService.get(merchantId)
}
