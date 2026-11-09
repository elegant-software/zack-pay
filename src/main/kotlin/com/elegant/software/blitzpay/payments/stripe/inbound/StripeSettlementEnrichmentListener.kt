package com.elegant.software.blitzpay.payments.stripe.inbound

import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.order.api.PaymentSettlementData
import com.elegant.software.blitzpay.order.api.PaymentSettlementGateway
import com.elegant.software.blitzpay.payments.stripe.api.StripeSettlementRefreshGateway
import com.elegant.software.blitzpay.payments.stripe.api.StripeSettlementRefreshItem
import com.elegant.software.blitzpay.payments.stripe.api.StripeSettlementRefreshResult
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.exception.StripeException
import com.stripe.model.BalanceTransaction
import com.stripe.model.Charge
import com.stripe.net.RequestOptions
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
internal class StripeSettlementEnrichmentListener(
    private val stripeProperties: StripeProperties,
    private val paymentSettlementGateway: PaymentSettlementGateway,
    private val stripeSettlementClient: StripeSettlementClient,
) {
    private val log = LoggerFactory.getLogger(StripeSettlementEnrichmentListener::class.java)

    @Async
    @EventListener
    fun on(event: StripePaymentSettled) {
        log.info(
            "settlement enrichment started paymentRequestId={} intentId={} connectedAccountId={}",
            event.paymentRequestId, event.stripePaymentIntentId, event.connectedAccountId,
        )

        val requestOptions = buildRequestOptions(event.connectedAccountId)

        val charge = stripeSettlementClient.fetchLatestCharge(event.stripePaymentIntentId, requestOptions)
        if (charge == null) {
            log.warn(
                "settlement enrichment skipped — no charge found for intentId={} paymentRequestId={}",
                event.stripePaymentIntentId, event.paymentRequestId,
            )
            return
        }

        val balanceTransactionId = charge.balanceTransaction
        if (balanceTransactionId == null) {
            log.warn(
                "settlement enrichment skipped — charge has no balance transaction chargeId={} paymentRequestId={}",
                charge.id, event.paymentRequestId,
            )
            return
        }
        val balanceTx = charge.balanceTransactionObject
            ?: stripeSettlementClient.fetchBalanceTransaction(balanceTransactionId, requestOptions)
        if (balanceTx == null) {
            log.warn(
                "settlement enrichment skipped — balance transaction unavailable balanceTransactionId={} paymentRequestId={}",
                balanceTransactionId, event.paymentRequestId,
            )
            return
        }

        val processingFeeMinor = stripeProcessingFee(balanceTx, event.applicationFeeAmountMinor ?: 0L)
        val netMinor = balanceTx.net

        val paymentMethodType = charge.paymentMethodDetails?.type ?: "unknown"
        val paymentMethod = mapStripePaymentMethod(paymentMethodType)
        val paymentMethodDetail = stripePaymentMethodDetail(charge)

        paymentSettlementGateway.record(
            PaymentSettlementData(
                paymentRequestId = event.paymentRequestId,
                provider = "STRIPE",
                currency = event.currency,
                grossAmountMinor = event.grossAmountMinor,
                processingFeeMinor = processingFeeMinor,
                platformFeeMinor = event.applicationFeeAmountMinor ?: 0L,
                netAmountMinor = netMinor,
                paymentMethod = paymentMethod,
                paymentMethodDetail = paymentMethodDetail,
                fundsAvailableAt = balanceTx.availableOn?.let(Instant::ofEpochSecond),
                providerPaymentReference = charge.paymentIntent ?: event.stripePaymentIntentId,
                providerChargeReference = charge.id,
                providerBalanceTxReference = balanceTx.id,
                providerDashboardUrl = stripeDashboardPaymentUrl(
                    stripeProperties.dashboardPlatformAccountId,
                    event.connectedAccountId,
                    charge.paymentIntent ?: event.stripePaymentIntentId,
                    charge.livemode == true,
                ),
                settledAt = event.occurredAt,
            )
        )
        log.info(
            "settlement enrichment completed paymentRequestId={} chargeId={} processingFeeMinor={} netMinor={} paymentMethod={}",
            event.paymentRequestId, charge.id, processingFeeMinor, netMinor, paymentMethod,
        )
    }

    private fun buildRequestOptions(connectedAccountId: String?): RequestOptions {
        val builder = RequestOptions.builder().setApiKey(stripeProperties.secretKey)
        if (connectedAccountId != null) builder.setStripeAccount(connectedAccountId)
        return builder.build()
    }
}

internal interface StripeSettlementClient {
    fun fetchLatestCharge(paymentIntentId: String, requestOptions: RequestOptions): Charge?
    fun fetchCharge(chargeId: String, requestOptions: RequestOptions): Charge?

    fun fetchBalanceTransaction(balanceTransactionId: String, requestOptions: RequestOptions): BalanceTransaction?
}

@Component
internal class StripeSettlementClientImpl : StripeSettlementClient {
    private val log = LoggerFactory.getLogger(StripeSettlementClientImpl::class.java)

    override fun fetchLatestCharge(paymentIntentId: String, requestOptions: RequestOptions): Charge? {
        return try {
            val charges = Charge.list(
                com.stripe.param.ChargeListParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setLimit(1L)
                    .build(),
                requestOptions,
            )
            val chargeId = charges.data.firstOrNull()?.id ?: return null
            Charge.retrieve(chargeId, requestOptions)
        } catch (ex: StripeException) {
            log.warn(
                "settlement enrichment failed to fetch charge for intentId={} code={} message={}",
                paymentIntentId, ex.code, ex.message,
            )
            null
        }
    }

    override fun fetchCharge(chargeId: String, requestOptions: RequestOptions): Charge? {
        return try {
            Charge.retrieve(chargeId, requestOptions)
        } catch (ex: StripeException) {
            log.warn(
                "settlement refresh failed to fetch charge id={} code={} message={}",
                chargeId, ex.code, ex.message,
            )
            null
        }
    }

    override fun fetchBalanceTransaction(
        balanceTransactionId: String,
        requestOptions: RequestOptions,
    ): BalanceTransaction? {
        return try {
            BalanceTransaction.retrieve(balanceTransactionId, requestOptions)
        } catch (ex: StripeException) {
            log.warn(
                "settlement enrichment failed to fetch balance transaction id={} code={} message={}",
                balanceTransactionId, ex.code, ex.message,
            )
            null
        }
    }
}

@Component
internal class StripeSettlementRefreshService(
    private val stripeProperties: StripeProperties,
    private val stripeConnectGateway: StripeConnectGateway,
    private val paymentSettlementGateway: PaymentSettlementGateway,
    private val stripeSettlementClient: StripeSettlementClient,
) : StripeSettlementRefreshGateway {
    override fun refresh(merchantId: UUID, paymentRequestIds: Set<UUID>): StripeSettlementRefreshResult {
        val candidates = paymentSettlementGateway.findStripeRefreshCandidates(merchantId, paymentRequestIds)
            .associateBy { it.paymentRequestId }
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
        val requestOptions = RequestOptions.builder()
            .setApiKey(stripeProperties.secretKey)
            .apply { connectedAccountId?.let(::setStripeAccount) }
            .build()

        val results = paymentRequestIds.map { paymentRequestId ->
            val candidate = candidates[paymentRequestId]
                ?: return@map StripeSettlementRefreshItem(paymentRequestId, "SKIPPED", "Stripe settlement not found for merchant")
            val chargeId = candidate.providerChargeReference
                ?: return@map StripeSettlementRefreshItem(paymentRequestId, "SKIPPED", "Stripe charge reference is missing")
            val charge = stripeSettlementClient.fetchCharge(chargeId, requestOptions)
                ?: return@map StripeSettlementRefreshItem(paymentRequestId, "FAILED", "Stripe charge could not be retrieved")
            val balanceTransactionId = charge.balanceTransaction
                ?: return@map StripeSettlementRefreshItem(paymentRequestId, "FAILED", "Stripe balance transaction is not available")
            val balanceTransaction = charge.balanceTransactionObject
                ?: stripeSettlementClient.fetchBalanceTransaction(balanceTransactionId, requestOptions)
                ?: return@map StripeSettlementRefreshItem(paymentRequestId, "FAILED", "Stripe balance transaction could not be retrieved")
            val paymentMethodType = charge.paymentMethodDetails?.type ?: "unknown"
            val refreshed = paymentSettlementGateway.refresh(
                PaymentSettlementData(
                    paymentRequestId = paymentRequestId,
                    provider = "STRIPE",
                    currency = candidate.currency,
                    grossAmountMinor = candidate.grossAmountMinor,
                    processingFeeMinor = stripeProcessingFee(balanceTransaction, candidate.platformFeeMinor),
                    platformFeeMinor = candidate.platformFeeMinor,
                    netAmountMinor = balanceTransaction.net,
                    paymentMethod = mapStripePaymentMethod(paymentMethodType),
                    paymentMethodDetail = stripePaymentMethodDetail(charge),
                    fundsAvailableAt = balanceTransaction.availableOn?.let(Instant::ofEpochSecond),
                    providerPaymentReference = charge.paymentIntent ?: candidate.providerPaymentReference,
                    providerChargeReference = charge.id,
                    providerBalanceTxReference = balanceTransaction.id,
                    providerDashboardUrl = stripeDashboardPaymentUrl(
                        stripeProperties.dashboardPlatformAccountId,
                        connectedAccountId,
                        charge.paymentIntent ?: candidate.providerPaymentReference,
                        charge.livemode == true,
                    ),
                    settledAt = candidate.settledAt,
                )
            )
            if (refreshed) {
                StripeSettlementRefreshItem(paymentRequestId, "REFRESHED", null)
            } else {
                StripeSettlementRefreshItem(paymentRequestId, "FAILED", "Settlement could not be updated")
            }
        }

        return StripeSettlementRefreshResult(
            requested = paymentRequestIds.size,
            refreshed = results.count { it.status == "REFRESHED" },
            skipped = results.count { it.status == "SKIPPED" },
            failed = results.count { it.status == "FAILED" },
            results = results,
        )
    }
}

internal fun stripeProcessingFee(balanceTransaction: BalanceTransaction, applicationFeeMinor: Long): Long {
    val feeDetails = balanceTransaction.feeDetails.orEmpty()
    if (feeDetails.isNotEmpty()) {
        return feeDetails
            .filterNot { it.type == "application_fee" }
            .sumOf { it.amount ?: 0L }
    }
    return ((balanceTransaction.fee ?: 0L) - applicationFeeMinor).coerceAtLeast(0L)
}

internal fun mapStripePaymentMethod(stripeType: String): String = when (stripeType) {
    "card" -> "CARD"
    "us_bank_account", "sepa_debit", "bacs_debit" -> "BANK_ACCOUNT"
    "pay_by_bank" -> "PAY_BY_BANK"
    "card_present", "interac_present" -> "TERMINAL"
    "paypal" -> "PAYPAL"
    else -> "CARD"
}

/** Card brand for online card charges; card network (`girocard`, `visa`, ...) for Terminal Payments. */
internal fun stripePaymentMethodDetail(charge: Charge): String? = when (charge.paymentMethodDetails?.type) {
    "card" -> charge.paymentMethodDetails?.card?.brand
    "card_present" -> charge.paymentMethodDetails?.cardPresent?.network
    else -> null
}

internal fun stripeDashboardPaymentUrl(
    platformAccountId: String,
    connectedAccountId: String?,
    paymentIntentId: String?,
    liveMode: Boolean,
): String? {
    if (platformAccountId.isBlank() || connectedAccountId.isNullOrBlank() || paymentIntentId.isNullOrBlank()) return null
    val modePath = if (liveMode) "" else "test/"
    return "https://dashboard.stripe.com/$platformAccountId/${modePath}connect/accounts/$connectedAccountId/payments/$paymentIntentId?redirected=true"
}
