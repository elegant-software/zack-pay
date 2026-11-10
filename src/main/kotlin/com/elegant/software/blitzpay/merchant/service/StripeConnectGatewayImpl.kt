package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.BranchTerminalInfo
import com.elegant.software.blitzpay.merchant.api.BranchTerminalLocation
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProviderAccount
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantStripeProviderAccount
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProviderAccountRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantStripeProviderAccountRepository
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
    private val branchRepository: MerchantBranchRepository,
    private val managementService: MerchantManagementService,
) : StripeConnectGateway {

    private val log = LoggerFactory.getLogger(StripeConnectGatewayImpl::class.java)

    override fun save(merchantId: UUID, connectedAccountId: String) {
        val providerAccount = providerAccountRepository
            .findByMerchantIdAndPspCode(merchantId, "STRIPE")
            ?: providerAccountRepository.save(MerchantProviderAccount(
                merchantId = merchantId,
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

    override fun isTapToPayEnabled(merchantId: UUID): Boolean =
        findStripeProviderAccount(merchantId)?.tapToPayEnabled ?: false

    override fun enableTapToPay(merchantId: UUID) {
        val record = findStripeProviderAccount(merchantId)
            ?: throw NoSuchElementException("No Stripe Connect account found for merchant $merchantId")
        record.tapToPayEnabled = true
        record.updatedAt = Instant.now()
        stripeProviderAccountRepository.save(record)
        log.info("stripe tap_to_pay enabled merchantId={}", merchantId)
    }

    override fun disableTapToPay(merchantId: UUID) {
        val record = findStripeProviderAccount(merchantId)
            ?: throw NoSuchElementException("No Stripe Connect account found for merchant $merchantId")
        record.tapToPayEnabled = false
        record.updatedAt = Instant.now()
        stripeProviderAccountRepository.save(record)
        log.info("stripe tap_to_pay disabled merchantId={}", merchantId)
    }

    override fun findTerminalLocationId(branchId: UUID): String? =
        branchRepository.findById(branchId).orElse(null)?.stripeTerminalLocationId

    override fun findTerminalLocations(merchantId: UUID): List<BranchTerminalLocation> =
        branchRepository.findAllByMerchantId(merchantId).mapNotNull { branch ->
            branch.stripeTerminalLocationId?.let { BranchTerminalLocation(branch.id, branch.name, it) }
        }

    override fun getBranchTerminalInfo(branchId: UUID): BranchTerminalInfo? {
        val branch = branchRepository.findById(branchId).orElse(null) ?: return null
        return BranchTerminalInfo(
            branchName = branch.name,
            addressLine1 = branch.address?.addressLine1,
            city = branch.address?.city,
            postalCode = branch.address?.postalCode,
            country = branch.address?.country,
        )
    }

    override fun saveTerminalLocationId(branchId: UUID, locationId: String) {
        val branch = branchRepository.findById(branchId).orElseThrow {
            NoSuchElementException("Branch $branchId not found")
        }
        branch.stripeTerminalLocationId = locationId
        branchRepository.save(branch)
        log.info("stripe terminal_location_id saved branchId={} locationId={}", branchId, locationId)
    }

    override fun getDetails(merchantId: UUID): MerchantDetailsResponse =
        managementService.get(merchantId)

    private fun findStripeProviderAccount(merchantId: UUID): MerchantStripeProviderAccount? {
        val providerAccount = providerAccountRepository
            .findByMerchantIdAndPspCode(merchantId, "STRIPE") ?: return null
        return stripeProviderAccountRepository.findById(providerAccount.id).orElse(null)
    }
}
