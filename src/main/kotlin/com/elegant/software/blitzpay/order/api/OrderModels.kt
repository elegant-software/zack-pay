package com.elegant.software.blitzpay.order.api

import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class PaymentMethod { TRUELAYER, QRPAY, BRAINTREE, STRIPE }

enum class PaymentSource { APP_PAYMENT, MANUAL_SETTLEMENT }

enum class CheckoutPaymentMethod { BANK_ACCOUNT, CARD, PAY_BY_BANK, TERMINAL, PAYPAL }

@JsonIgnoreProperties(ignoreUnknown = true)
data class AddOrderItemRequest(
    val productId: UUID,
    val quantity: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdateOrderItemRequest(
    val quantity: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MerchantItemPriceOverrideRequest(
    val unitPriceMinor: Long,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SettleOrderRequest(
    val note: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderCustomerLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAt: Instant,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuyerContactRequest(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
)

data class BuyerContactResponse(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateOrderRequest(
    val branchId: UUID? = null,
    val orderType: OrderType = OrderType.PRE_ORDER,
    val usesDeferredPayment: Boolean = false,
    val customerLocation: OrderCustomerLocationRequest? = null,
    val items: List<CreateOrderItemRequest> = emptyList(),
    val paymentMethod: PaymentMethod? = null,
    val checkoutPaymentMethod: CheckoutPaymentMethod? = null,
    val buyerContact: BuyerContactRequest? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateMerchantOrderRequest(
    val merchantId: UUID,
    val branchId: UUID? = null,
    val orderType: OrderType = OrderType.WALK_IN_ORDERING,
    val usesDeferredPayment: Boolean = false,
    val targetShopperId: String? = null,
    val dueDate: LocalDate? = null,
    val customerLocation: OrderCustomerLocationRequest? = null,
    val items: List<CreateOrderItemRequest> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateOrderItemRequest(
    val productId: UUID,
    val quantity: Int,
)

data class OrderItemResponse(
    val itemId: UUID,
    val productId: UUID,
    val name: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
)

data class PaymentReferenceResponse(
    val provider: String,
    val paymentRequestId: UUID,
)

data class QrCodeResponse(
    val paymentUrl: String,
)

data class OrderResponse(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val orderType: OrderType = OrderType.PRE_ORDER,
    val usesDeferredPayment: Boolean = false,
    val targetShopperId: String? = null,
    val dueDate: LocalDate? = null,
    val status: OrderStatus,
    val creatorType: CreatorType,
    val createdById: String,
    val currency: String,
    val totalAmountMinor: Long,
    val paymentRetryAllowed: Boolean,
    val paymentSource: PaymentSource? = null,
    val checkoutPaymentMethod: CheckoutPaymentMethod? = null,
    val buyerContact: BuyerContactResponse? = null,
    val items: List<OrderItemResponse>,
    val createdAt: Instant,
    val lastPaymentRequestId: UUID? = null,
    val lastPaymentProvider: String? = null,
    val paidAt: Instant? = null,
    val paymentReference: PaymentReferenceResponse? = null,
)

data class MerchantOrderResponse(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val orderType: OrderType = OrderType.WALK_IN_ORDERING,
    val usesDeferredPayment: Boolean = false,
    val targetShopperId: String? = null,
    val dueDate: LocalDate? = null,
    val status: OrderStatus,
    val creatorType: CreatorType,
    val currency: String,
    val totalAmountMinor: Long,
    val paymentRetryAllowed: Boolean,
    val checkoutPaymentMethod: CheckoutPaymentMethod? = null,
    val qrCode: QrCodeResponse? = null,
    val items: List<OrderItemResponse>,
    val createdAt: Instant,
)

data class OrderSummaryResponse(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val orderType: OrderType = OrderType.PRE_ORDER,
    val usesDeferredPayment: Boolean = false,
    val targetShopperId: String? = null,
    val dueDate: LocalDate? = null,
    val status: OrderStatus,
    val currency: String,
    val totalAmountMinor: Long,
    val paymentRetryAllowed: Boolean,
    val paymentSource: PaymentSource? = null,
    val checkoutPaymentMethod: CheckoutPaymentMethod? = null,
    val createdAt: Instant,
)

data class BranchCustomerResponse(
    val shopperAccountId: String,
    val name: String,
)

data class OrderPaymentInitiationRequested(
    val orderId: String,
    val paymentMethod: PaymentMethod,
    val amountMinorUnits: Long,
    val currency: String,
    val paymentRequestId: UUID,
    val merchantId: UUID,
    val branchId: UUID?,
    val redirectReturnUri: String = "blitzpay://payment/return",
)

internal fun Order.toResponse(items: List<OrderItem>, paymentReference: PaymentReferenceResponse? = null) = OrderResponse(
    orderId = orderId,
    merchantId = merchantApplicationId,
    branchId = merchantBranchId,
    orderType = orderType,
    usesDeferredPayment = usesDeferredPayment,
    targetShopperId = targetShopperId,
    dueDate = dueDate,
    status = status,
    creatorType = creatorType,
    createdById = createdById,
    currency = currency,
    totalAmountMinor = totalAmountMinor,
    paymentRetryAllowed = status.paymentRetryAllowed,
    paymentSource = paymentSource?.let { runCatching { PaymentSource.valueOf(it) }.getOrNull() },
    checkoutPaymentMethod = checkoutPaymentMethod?.let { runCatching { CheckoutPaymentMethod.valueOf(it) }.getOrNull() },
    buyerContact = run {
        val name = buyerContactName
        val street = buyerContactStreet
        val zip = buyerContactZip
        val city = buyerContactCity
        val country = buyerContactCountry
        if (name != null && street != null && zip != null && city != null && country != null)
            BuyerContactResponse(name, street, zip, city, country)
        else null
    },
    items = items.sortedBy { it.createdAt }.map {
        OrderItemResponse(
            itemId = it.id,
            productId = it.merchantProductId,
            name = it.productName,
            quantity = it.quantity,
            unitPriceMinor = it.effectiveUnitPriceMinor,
            lineTotalMinor = it.lineTotalMinor,
        )
    },
    createdAt = createdAt,
    lastPaymentRequestId = lastPaymentRequestId,
    lastPaymentProvider = lastPaymentProvider,
    paidAt = paidAt,
    paymentReference = paymentReference,
)

internal fun Order.toSummaryResponse() = OrderSummaryResponse(
    orderId = orderId,
    merchantId = merchantApplicationId,
    branchId = merchantBranchId,
    orderType = orderType,
    usesDeferredPayment = usesDeferredPayment,
    targetShopperId = targetShopperId,
    dueDate = dueDate,
    status = status,
    currency = currency,
    totalAmountMinor = totalAmountMinor,
    paymentRetryAllowed = status.paymentRetryAllowed,
    paymentSource = paymentSource?.let { runCatching { PaymentSource.valueOf(it) }.getOrNull() },
    checkoutPaymentMethod = checkoutPaymentMethod?.let { runCatching { CheckoutPaymentMethod.valueOf(it) }.getOrNull() },
    createdAt = createdAt,
)

internal fun Order.toMerchantResponse(items: List<OrderItem>, qrPaymentUrl: String? = null) = MerchantOrderResponse(
    orderId = orderId,
    merchantId = merchantApplicationId,
    branchId = merchantBranchId,
    orderType = orderType,
    usesDeferredPayment = usesDeferredPayment,
    targetShopperId = targetShopperId,
    dueDate = dueDate,
    status = status,
    creatorType = creatorType,
    currency = currency,
    totalAmountMinor = totalAmountMinor,
    paymentRetryAllowed = status.paymentRetryAllowed,
    checkoutPaymentMethod = checkoutPaymentMethod?.let { runCatching { CheckoutPaymentMethod.valueOf(it) }.getOrNull() },
    qrCode = qrPaymentUrl?.let { QrCodeResponse(paymentUrl = it) },
    items = items.sortedBy { it.createdAt }.map {
        OrderItemResponse(
            itemId = it.id,
            productId = it.merchantProductId,
            name = it.productName,
            quantity = it.quantity,
            unitPriceMinor = it.unitPriceMinor,
            lineTotalMinor = it.lineTotalMinor,
        )
    },
    createdAt = createdAt,
)
