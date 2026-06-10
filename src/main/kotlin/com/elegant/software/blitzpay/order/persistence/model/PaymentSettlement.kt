package com.elegant.software.blitzpay.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payment_settlements", schema = "blitzpay")
class PaymentSettlement(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "payment_request_id", nullable = false, unique = true, updatable = false)
    val paymentRequestId: UUID,

    @Column(name = "order_id_fk", nullable = false, updatable = false)
    val orderIdFk: UUID,

    @Column(name = "order_id", nullable = false, updatable = false, length = 64)
    val orderId: String,

    @Column(nullable = false, length = 32)
    val provider: String,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "gross_amount_minor", nullable = false)
    val grossAmountMinor: Long,

    @Column(name = "processing_fee_minor", nullable = false)
    var processingFeeMinor: Long,

    @Column(name = "platform_fee_minor", nullable = false)
    var platformFeeMinor: Long,

    @Column(name = "net_amount_minor", nullable = false)
    var netAmountMinor: Long,

    @Column(name = "payment_method", nullable = false, length = 32)
    var paymentMethod: String,

    @Column(name = "payment_method_detail", length = 64)
    var paymentMethodDetail: String? = null,

    @Column(name = "funds_available_at")
    var fundsAvailableAt: Instant? = null,

    @Column(name = "provider_payment_reference", length = 255)
    var providerPaymentReference: String? = null,

    @Column(name = "provider_charge_reference", length = 255)
    var providerChargeReference: String? = null,

    @Column(name = "provider_balance_tx_reference", length = 255)
    var providerBalanceTxReference: String? = null,

    @Column(name = "provider_dashboard_url", length = 1000)
    var providerDashboardUrl: String? = null,

    @Column(name = "settled_at", nullable = false, updatable = false)
    val settledAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    fun refreshFrom(data: com.elegant.software.blitzpay.order.api.PaymentSettlementData) {
        processingFeeMinor = data.processingFeeMinor
        platformFeeMinor = data.platformFeeMinor
        netAmountMinor = data.netAmountMinor
        paymentMethod = data.paymentMethod
        paymentMethodDetail = data.paymentMethodDetail
        fundsAvailableAt = data.fundsAvailableAt
        providerPaymentReference = data.providerPaymentReference
        providerChargeReference = data.providerChargeReference
        providerBalanceTxReference = data.providerBalanceTxReference
        providerDashboardUrl = data.providerDashboardUrl
    }
}
