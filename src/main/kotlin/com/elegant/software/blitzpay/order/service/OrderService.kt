package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.merchant.api.ActiveBranchInfo
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct
import com.elegant.software.blitzpay.order.api.PaymentMethod
import com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway
import com.elegant.software.blitzpay.order.api.AddOrderItemRequest
import com.elegant.software.blitzpay.order.api.BuyerContactRequest
import com.elegant.software.blitzpay.order.api.CheckoutPaymentMethod
import com.elegant.software.blitzpay.order.api.CreateMerchantOrderRequest
import com.elegant.software.blitzpay.order.api.CreateOrderRequest
import com.elegant.software.blitzpay.order.api.MerchantItemPriceOverrideRequest
import com.elegant.software.blitzpay.order.api.OrderCustomerLocationRequest
import com.elegant.software.blitzpay.order.api.MerchantOrderResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.OrderSummaryResponse
import com.elegant.software.blitzpay.order.api.SettleOrderRequest
import com.elegant.software.blitzpay.order.api.UpdateOrderItemRequest
import com.elegant.software.blitzpay.order.api.toMerchantResponse
import com.elegant.software.blitzpay.order.api.toResponse
import com.elegant.software.blitzpay.order.api.toSummaryResponse
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.elegant.software.blitzpay.order.persistence.model.unitPriceMinor
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import com.elegant.software.blitzpay.order.persistence.repository.PaymentAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import java.util.UUID

@Service
@Transactional
class OrderService(
    private val merchantGateway: MerchantGateway,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val shopperProfileGateway: ShopperProfileGateway,
) {
    private val log = LoggerFactory.getLogger(OrderService::class.java)

    fun createShopperOrder(request: CreateOrderRequest, shopperId: String): OrderResponse {
        requireNotNull(request.branchId) { "branchId is required" }
        require(request.items.isNotEmpty()) { "Order must contain at least one item" }
        request.items.forEach { require(it.quantity > 0) { "quantity must be > 0 for product ${it.productId}" } }

        val products = merchantGateway.findOrderableProducts(request.items.map { it.productId }.distinct())
        val productsById = products.associateBy { it.productId }
        validateProducts(request, productsById)
        val merchantId = products.map { it.merchantApplicationId }.distinct().single()
        val branch = requireActiveBranch(merchantId, request.branchId)
        validateOfferingRules(merchantId, request.orderType, request.usesDeferredPayment)
        validateWalkInLocationIfNeeded(branch, request.orderType, request.customerLocation)

        val resolvedBuyerContact = request.buyerContact
            ?: shopperProfileGateway.findByShopperId(shopperId)?.let { p ->
                BuyerContactRequest(name = p.name, street = p.street, zip = p.zip, city = p.city, country = p.country)
            }

        val order = Order(
            orderId = Order.nextOrderId(),
            merchantApplicationId = merchantId,
            merchantBranchId = request.branchId,
            orderType = request.orderType,
            usesDeferredPayment = request.usesDeferredPayment,
            creatorType = CreatorType.SHOPPER,
            createdById = shopperId,
            currency = DEFAULT_CURRENCY,
            totalAmountMinor = request.items.sumOf { productsById.getValue(it.productId).unitPriceMinor() * it.quantity },
            itemCount = request.items.sumOf { it.quantity },
            checkoutPaymentMethod = resolveCheckoutPaymentMethod(request)?.name,
            buyerContactName = resolvedBuyerContact?.name,
            buyerContactStreet = resolvedBuyerContact?.street,
            buyerContactZip = resolvedBuyerContact?.zip,
            buyerContactCity = resolvedBuyerContact?.city,
            buyerContactCountry = resolvedBuyerContact?.country,
        )
        val savedOrder = orderRepository.save(order)
        val savedItems = orderItemRepository.saveAll(
            request.items.map { OrderItem.fromProduct(savedOrder.id, productsById.getValue(it.productId), it.quantity) }
        )
        LogContext.with(LogContext.ORDER_ID to savedOrder.orderId) {
            log.info(
                "order saved orderId={} merchantId={} branchId={} totalAmountMinor={} currency={} itemCount={}",
                savedOrder.orderId, savedOrder.merchantApplicationId, savedOrder.merchantBranchId,
                savedOrder.totalAmountMinor, savedOrder.currency, savedOrder.itemCount,
            )
        }
        return savedOrder.toResponse(savedItems)
    }

    fun createMerchantOrder(request: CreateMerchantOrderRequest, merchantUserId: String): MerchantOrderResponse {
        requireNotNull(request.branchId) { "branchId is required" }
        require(request.items.isNotEmpty()) { "Order must contain at least one item" }
        request.items.forEach { require(it.quantity > 0) { "quantity must be > 0 for product ${it.productId}" } }

        val products = merchantGateway.findOrderableProducts(request.items.map { it.productId }.distinct())
        val productsById = products.associateBy { it.productId }
        validateProductsForMerchant(request, productsById)
        requireActiveBranch(request.merchantId, request.branchId)
        validateOfferingRules(request.merchantId, request.orderType, request.usesDeferredPayment)

        val order = Order(
            orderId = Order.nextOrderId(),
            merchantApplicationId = request.merchantId,
            merchantBranchId = request.branchId,
            orderType = request.orderType,
            usesDeferredPayment = request.usesDeferredPayment,
            creatorType = CreatorType.MERCHANT,
            createdById = merchantUserId,
            currency = DEFAULT_CURRENCY,
            totalAmountMinor = request.items.sumOf { productsById.getValue(it.productId).unitPriceMinor() * it.quantity },
            itemCount = request.items.sumOf { it.quantity },
        )
        val savedOrder = orderRepository.save(order)
        val savedItems = orderItemRepository.saveAll(
            request.items.map { OrderItem.fromProduct(savedOrder.id, productsById.getValue(it.productId), it.quantity) }
        )

        val qrUrl = buildQrPaymentUrl(savedOrder.orderId, savedOrder.totalAmountMinor, savedOrder.currency)
        return savedOrder.toMerchantResponse(savedItems, qrUrl)
    }

    @Transactional(readOnly = true)
    fun get(orderId: String): OrderResponse {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        return order.toResponse(items)
    }

    @Transactional(readOnly = true)
    fun listShopperOrders(shopperId: String): List<OrderSummaryResponse> {
        val cutoff = Instant.now().minus(7, ChronoUnit.DAYS)
        return orderRepository
            .findByCreatedByIdAndCreatorTypeAndCreatedAtAfter(shopperId, CreatorType.SHOPPER, cutoff)
            .map { it.toSummaryResponse() }
    }

    @Transactional(readOnly = true)
    fun listMerchantOrders(branchId: UUID, status: OrderStatus?, timezone: ZoneId = ZoneId.of("UTC")): List<OrderResponse> {
        val today = LocalDate.now(timezone)
        val from = today.atStartOfDay(timezone).toInstant()
        val to = today.plusDays(1).atStartOfDay(timezone).toInstant()
        val orders = if (status != null) {
            orderRepository.findByMerchantBranchIdAndStatusAndCreatedAtBetween(branchId, status, from, to)
        } else {
            orderRepository.findByMerchantBranchIdAndCreatedAtBetween(branchId, from, to)
        }
        return orders.map { order ->
            val items = orderItemRepository.findAllByOrderIdFk(order.id)
            order.toResponse(items)
        }
    }

    fun addItem(orderId: String, request: AddOrderItemRequest, actorId: String): OrderResponse {
        require(request.quantity > 0) { "quantity must be > 0" }
        val order = requireMutableOrder(orderId)
        val products = merchantGateway.findOrderableProducts(listOf(request.productId))
        val product = products.firstOrNull { it.productId == request.productId }
            ?: throw NoSuchElementException("Product not found: ${request.productId}")
        if (!product.active) throw OrderMutationConflictException("Product is not orderable: ${request.productId}")
        if (product.merchantApplicationId != order.merchantApplicationId) {
            throw OrderMutationConflictException("Product does not belong to the order's merchant")
        }
        val newItem = OrderItem.fromProduct(order.id, product, request.quantity)
        orderItemRepository.save(newItem)
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        order.recalculateTotals(items)
        orderRepository.save(order)
        return order.toResponse(items)
    }

    fun updateItemQuantity(orderId: String, itemId: UUID, request: UpdateOrderItemRequest, actorId: String): OrderResponse {
        require(request.quantity > 0) { "quantity must be > 0" }
        val order = requireMutableOrder(orderId)
        val item = requireOrderItem(order.id, itemId)
        item.updateQuantity(request.quantity)
        orderItemRepository.save(item)
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        order.recalculateTotals(items)
        orderRepository.save(order)
        return order.toResponse(items)
    }

    fun removeItem(orderId: String, itemId: UUID, actorId: String): OrderResponse {
        val order = requireMutableOrder(orderId)
        requireOrderItem(order.id, itemId)
        val allItems = orderItemRepository.findAllByOrderIdFk(order.id)
        return if (allItems.size <= 1) {
            order.status = OrderStatus.CANCELLED
            order.updatedAt = java.time.Instant.now()
            orderRepository.save(order)
            order.toResponse(allItems)
        } else {
            orderItemRepository.deleteById(itemId)
            val remaining = allItems.filter { it.id != itemId }
            order.recalculateTotals(remaining)
            orderRepository.save(order)
            order.toResponse(remaining)
        }
    }

    fun cancelOrder(orderId: String, actorId: String): OrderResponse {
        val order = requireMutableOrder(orderId)
        order.status = OrderStatus.CANCELLED
        order.updatedAt = java.time.Instant.now()
        orderRepository.save(order)
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        return order.toResponse(items)
    }

    fun applyMerchantPriceOverride(
        orderId: String,
        itemId: UUID,
        request: MerchantItemPriceOverrideRequest,
        merchantUserId: String,
    ): OrderResponse {
        val order = requireMutableOrder(orderId)
        val item = requireOrderItem(order.id, itemId)
        item.applyPriceOverride(request.unitPriceMinor, merchantUserId)
        orderItemRepository.save(item)
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        order.recalculateTotals(items)
        orderRepository.save(order)
        return order.toResponse(items)
    }

    fun manualSettle(orderId: String, request: SettleOrderRequest, merchantUserId: String): OrderResponse {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        if (order.status == OrderStatus.PAID) {
            throw OrderMutationConflictException("Order is already paid: $orderId")
        }
        if (order.status == OrderStatus.CANCELLED) {
            throw OrderMutationConflictException("Order is cancelled and cannot be settled: $orderId")
        }
        order.manualSettle(request.note, merchantUserId)
        orderRepository.save(order)
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        return order.toResponse(items)
    }

    @Transactional(readOnly = true)
    fun getMerchantOrder(orderId: String): OrderResponse {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        return order.toResponse(items)
    }

    private fun requireMutableOrder(orderId: String): Order {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        if (order.status != OrderStatus.CREATED) {
            throw OrderMutationConflictException(
                "Order cannot be mutated in status: ${order.status}"
            )
        }
        return order
    }

    private fun requireOrderItem(orderIdFk: UUID, itemId: UUID): OrderItem {
        val items = orderItemRepository.findAllByOrderIdFk(orderIdFk)
        return items.firstOrNull { it.id == itemId }
            ?: throw NoSuchElementException("Order item not found: $itemId")
    }

    fun deleteOrder(orderId: UUID) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order not found: $orderId") }
        paymentAttemptRepository.deleteAllByOrderIdFk(order.id)
        orderItemRepository.deleteAllByOrderIdFk(order.id)
        orderRepository.delete(order)
        log.info("order deleted id={}", orderId)
    }

    private fun validateProducts(
        request: CreateOrderRequest,
        productsById: Map<UUID, OrderableMerchantProduct>,
    ) {
        val requestedIds = request.items.map { it.productId }.distinct()
        val missing = requestedIds.filterNot(productsById::containsKey)
        if (missing.isNotEmpty()) throw NoSuchElementException("Products not found: ${missing.joinToString(",")}")

        val inactive = productsById.values.filterNot { it.active }.map { it.productId }
        if (inactive.isNotEmpty()) throw OrderCreationConflictException("Products are not orderable: ${inactive.joinToString(",")}")

        val merchantIds = productsById.values.map { it.merchantApplicationId }.distinct()
        if (merchantIds.size != 1) throw OrderCreationConflictException("All ordered products must belong to the same merchant")
        val branchIds = productsById.values.mapNotNull { it.branchId }.distinct()
        if (branchIds.size != 1) throw OrderCreationConflictException("All ordered products must belong to the same branch")
        if (branchIds.single() != request.branchId) {
            throw OrderCreationConflictException("Ordered products do not belong to branch ${request.branchId}")
        }
    }

    private fun validateProductsForMerchant(
        request: CreateMerchantOrderRequest,
        productsById: Map<UUID, OrderableMerchantProduct>,
    ) {
        val requestedIds = request.items.map { it.productId }.distinct()
        val missing = requestedIds.filterNot(productsById::containsKey)
        if (missing.isNotEmpty()) throw NoSuchElementException("Products not found: ${missing.joinToString(",")}")

        val inactive = productsById.values.filterNot { it.active }.map { it.productId }
        if (inactive.isNotEmpty()) throw OrderCreationConflictException("Products are not orderable: ${inactive.joinToString(",")}")

        val wrongMerchant = productsById.values.filter { it.merchantApplicationId != request.merchantId }.map { it.productId }
        if (wrongMerchant.isNotEmpty()) throw OrderCreationConflictException("Products do not belong to merchant: ${wrongMerchant.joinToString(",")}")
        val wrongBranch = productsById.values.filter { it.branchId != request.branchId }.map { it.productId }
        if (wrongBranch.isNotEmpty()) throw OrderCreationConflictException("Products do not belong to branch: ${wrongBranch.joinToString(",")}")
    }

    private fun requireActiveBranch(merchantId: UUID, branchId: UUID): ActiveBranchInfo =
        try {
            merchantGateway.requireActiveBranch(merchantId, branchId)
        } catch (ex: IllegalStateException) {
            throw OrderCreationConflictException(ex.message ?: "Branch validation failed")
        }

    private fun validateOfferingRules(merchantId: UUID, orderType: OrderType, usesDeferredPayment: Boolean) {
        val enabled = merchantGateway.getEnabledOfferings(merchantId)
        val requiredOffering = when (orderType) {
            OrderType.PRE_ORDER -> "PRE_ORDER"
            OrderType.WALK_IN_ORDERING -> "WALK_IN_ORDERING"
        }
        if (requiredOffering !in enabled) {
            throw OrderCreationConflictException("Merchant does not support $requiredOffering")
        }
        if (usesDeferredPayment && "DEFERRED_PAYMENT" !in enabled) {
            throw OrderCreationConflictException("Merchant does not support DEFERRED_PAYMENT")
        }
    }

    private fun validateWalkInLocationIfNeeded(
        branch: ActiveBranchInfo,
        orderType: OrderType,
        customerLocation: OrderCustomerLocationRequest?,
    ) {
        if (orderType != OrderType.WALK_IN_ORDERING) {
            return
        }
        requireNotNull(customerLocation) { "customerLocation is required for WALK_IN_ORDERING" }
        require(customerLocation.accuracyMeters in 0.0..MAX_WALK_IN_ACCURACY_METERS) {
            "customerLocation accuracy is insufficient for WALK_IN_ORDERING"
        }
        require(customerLocation.capturedAt.isAfter(Instant.now().minus(MAX_WALK_IN_LOCATION_AGE))) {
            "customerLocation is too old for WALK_IN_ORDERING"
        }
        val location = requireNotNull(branch.location) { "Branch location is required for WALK_IN_ORDERING" }
        val distanceMeters = haversineMeters(
            customerLocation.latitude,
            customerLocation.longitude,
            location.latitude,
            location.longitude,
        )
        if (distanceMeters > location.geofenceRadiusMeters) {
            throw OrderCreationConflictException("Customer is outside the branch geofence")
        }
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        return r * acos(
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2))
                * cos(Math.toRadians(lng2) - Math.toRadians(lng1))
                + sin(Math.toRadians(lat1)) * sin(Math.toRadians(lat2))
        )
    }

    private fun buildQrPaymentUrl(orderId: String, amountMinorUnits: Long, currency: String): String =
        "blitzpay://payment/qr?orderId=$orderId&amount=$amountMinorUnits&currency=$currency"

    private fun resolveCheckoutPaymentMethod(request: CreateOrderRequest): CheckoutPaymentMethod? =
        request.checkoutPaymentMethod ?: when (request.paymentMethod) {
            PaymentMethod.TRUELAYER,
            PaymentMethod.QRPAY -> CheckoutPaymentMethod.BANK_ACCOUNT
            PaymentMethod.BRAINTREE -> CheckoutPaymentMethod.PAYPAL
            PaymentMethod.STRIPE,
            null -> null
        }

    companion object {
        private const val DEFAULT_CURRENCY = "EUR"
        private val MAX_WALK_IN_LOCATION_AGE = ChronoUnit.MINUTES.duration.multipliedBy(5)
        private const val MAX_WALK_IN_ACCURACY_METERS = 100.0
    }
}

class OrderCreationConflictException(message: String) : IllegalStateException(message)
class OrderMutationConflictException(message: String) : IllegalStateException(message)
class UnauthenticatedException(message: String = "Valid authentication is required") : IllegalStateException(message)
