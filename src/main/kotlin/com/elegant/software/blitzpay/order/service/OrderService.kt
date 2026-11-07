package com.elegant.software.blitzpay.order.service

import org.springframework.http.HttpStatus
import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.merchant.api.ActiveBranchInfo
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct
import com.elegant.software.blitzpay.merchant.api.TableGateway
import com.elegant.software.blitzpay.merchant.api.TableInfo
import com.elegant.software.blitzpay.order.api.PaymentMethod
import com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway
import com.elegant.software.blitzpay.order.api.AddOrderItemRequest
import com.elegant.software.blitzpay.order.api.BranchCustomerResponse
import com.elegant.software.blitzpay.order.api.BuyerContactRequest
import com.elegant.software.blitzpay.order.api.CheckoutPaymentMethod
import com.elegant.software.blitzpay.order.api.CreateMerchantOrderRequest
import com.elegant.software.blitzpay.order.api.CreateOrderRequest
import com.elegant.software.blitzpay.order.api.InvoiceOrderCreated
import com.elegant.software.blitzpay.order.api.KitchenEventType
import com.elegant.software.blitzpay.order.api.toKitchenOrder
import com.elegant.software.blitzpay.order.api.OrderItemSnapshot
import com.elegant.software.blitzpay.order.api.MerchantItemPriceOverrideRequest
import com.elegant.software.blitzpay.order.api.OrderCustomerLocationRequest
import com.elegant.software.blitzpay.order.api.MerchantOrderResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.OrderSummaryResponse
import com.elegant.software.blitzpay.order.api.SettleOrderRequest
import com.elegant.software.blitzpay.order.api.StorefrontOrderRequest
import com.elegant.software.blitzpay.order.api.UpdateOrderItemRequest
import com.elegant.software.blitzpay.order.api.toMerchantResponse
import com.elegant.software.blitzpay.order.api.toResponse
import com.elegant.software.blitzpay.order.api.toSummaryResponse
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.elegant.software.blitzpay.order.persistence.model.PreparationStatus
import com.elegant.software.blitzpay.order.persistence.model.unitPriceMinor
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import com.elegant.software.blitzpay.order.persistence.repository.PaymentAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
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
    private val eventPublisher: ApplicationEventPublisher,
    private val pickupNumberAllocator: PickupNumberAllocator,
    private val kitchenOrderBus: KitchenOrderBus,
    private val storefrontOrderBus: StorefrontOrderBus,
    private val tableGateway: TableGateway,
) {
    private val log = LoggerFactory.getLogger(OrderService::class.java)

    fun createShopperOrder(request: CreateOrderRequest, shopperId: String): OrderResponse =
        createCustomerOrder(request, CreatorType.SHOPPER, shopperId) {
            request.buyerContact ?: shopperProfileGateway.findByShopperId(shopperId)?.let { p ->
                BuyerContactRequest(name = p.name, street = p.street, zip = p.zip, city = p.city, country = p.country)
            }
        }

    /**
     * Web storefront guest order. Priced from the catalog like a shopper order; the returned raw token is the
     * only proof of ownership and is never stored (only its hash).
     */
    fun createGuestOrder(request: StorefrontOrderRequest): GuestOrderCreated {
        require(request.items.size <= MAX_GUEST_ORDER_LINES) { "Order must not contain more than $MAX_GUEST_ORDER_LINES items" }
        request.items.forEach { require(it.quantity <= MAX_GUEST_ITEM_QUANTITY) { "quantity must be <= $MAX_GUEST_ITEM_QUANTITY for product ${it.productId}" } }
        // Guests pay immediately (card / Pay by Bank on the payment endpoint) or in cash; never Deferred Payment.
        require(request.checkoutPaymentMethod == null || request.checkoutPaymentMethod == CheckoutPaymentMethod.CASH) {
            "checkoutPaymentMethod must be CASH or omitted for storefront orders"
        }
        val tableCode = request.tableCode?.takeIf { it.isNotBlank() }
        val token = GuestToken.generate()
        val order = createCustomerOrder(
            CreateOrderRequest(
                branchId = request.branchId,
                orderType = if (tableCode != null) OrderType.WALK_IN_ORDERING else OrderType.PRE_ORDER,
                usesDeferredPayment = false,
                customerLocation = request.customerLocation,
                items = request.items,
                checkoutPaymentMethod = request.checkoutPaymentMethod,
                tableCode = tableCode,
                guestCount = request.guestCount,
            ),
            CreatorType.GUEST,
            GUEST_CREATOR_ID,
            guestTokenHash = GuestToken.hash(token),
            guestTokenExpiresAt = Instant.now().plus(GuestToken.TTL),
        ) { null }
        return GuestOrderCreated(order, token)
    }

    /** Unknown order and wrong/expired token look the same to the caller, so the endpoint can't be used as an order-ID oracle. */
    @Transactional(readOnly = true)
    fun getGuestOrder(orderId: String, token: String?): OrderResponse {
        val order = orderRepository.findByOrderId(orderId)?.takeIf { GuestToken.matches(it, token) }
            ?: throw NoSuchElementException("Order not found: $orderId")
        return order.toResponse(orderItemRepository.findAllByOrderIdFk(order.id))
    }

    private fun createCustomerOrder(
        request: CreateOrderRequest,
        creatorType: CreatorType,
        createdById: String,
        guestTokenHash: String? = null,
        guestTokenExpiresAt: Instant? = null,
        buyerContact: () -> BuyerContactRequest?,
    ): OrderResponse {
        requireNotNull(request.branchId) { "branchId is required" }
        require(request.items.isNotEmpty()) { "Order must contain at least one item" }
        request.items.forEach { require(it.quantity > 0) { "quantity must be > 0 for product ${it.productId}" } }
        request.guestCount?.let { require(it in 1..MAX_GUEST_COUNT) { "guestCount must be between 1 and $MAX_GUEST_COUNT" } }

        val products = merchantGateway.findOrderableProducts(request.items.map { it.productId }.distinct())
        val productsById = products.associateBy { it.productId }
        validateProducts(request, productsById)
        val merchantId = products.map { it.merchantId }.distinct().single()
        val branch = requireActiveBranch(merchantId, request.branchId)
        val table = request.tableCode?.takeIf { it.isNotBlank() }?.let { requireOrderableTable(it, request) }
        validateOfferingRules(merchantId, request.orderType, request.usesDeferredPayment)
        validateWalkInLocationIfNeeded(branch, request.orderType, request.customerLocation)

        val resolvedBuyerContact = buyerContact()

        val order = Order(
            orderId = Order.nextOrderId(),
            merchantApplicationId = merchantId,
            merchantBranchId = request.branchId,
            orderType = request.orderType,
            usesDeferredPayment = request.usesDeferredPayment,
            creatorType = creatorType,
            createdById = createdById,
            currency = DEFAULT_CURRENCY,
            totalAmountMinor = request.items.sumOf { productsById.getValue(it.productId).unitPriceMinor() * it.quantity },
            itemCount = request.items.sumOf { it.quantity },
            checkoutPaymentMethod = resolveCheckoutPaymentMethod(request)?.name,
            guestTokenHash = guestTokenHash,
            guestTokenExpiresAt = guestTokenExpiresAt,
            buyerContactName = resolvedBuyerContact?.name,
            buyerContactStreet = resolvedBuyerContact?.street,
            buyerContactZip = resolvedBuyerContact?.zip,
            buyerContactCity = resolvedBuyerContact?.city,
            buyerContactCountry = resolvedBuyerContact?.country,
            pickupNumber = pickupNumberAllocator.next(request.branchId),
            tableId = table?.tableId,
            tableName = table?.name,
        )
        val savedOrder = orderRepository.save(order)
        val savedItems = orderItemRepository.saveAll(
            request.items.map { OrderItem.fromProduct(savedOrder.id, productsById.getValue(it.productId), it.quantity) }
        )
        if (table != null && request.guestCount != null) tableGateway.seatGuestsIfUnset(table.tableId, request.guestCount)
        notifyKitchen(savedOrder, savedItems, KitchenEventType.CREATED)
        LogContext.with(LogContext.ORDER_ID to savedOrder.orderId) {
            log.info(
                "order saved orderId={} merchantId={} branchId={} tableId={} totalAmountMinor={} currency={} itemCount={}",
                savedOrder.orderId, savedOrder.merchantApplicationId, savedOrder.merchantBranchId, savedOrder.tableId,
                savedOrder.totalAmountMinor, savedOrder.currency, savedOrder.itemCount,
            )
        }
        return savedOrder.toResponse(savedItems)
    }

    fun createMerchantOrder(request: CreateMerchantOrderRequest, merchantUserId: String): MerchantOrderResponse {
        requireNotNull(request.branchId) { "branchId is required" }
        require(request.items.isNotEmpty()) { "Order must contain at least one item" }
        request.items.forEach { require(it.quantity > 0) { "quantity must be > 0 for product ${it.productId}" } }
        if (request.targetShopperId != null) {
            require(request.usesDeferredPayment) { "usesDeferredPayment must be true when targetShopperId is set" }
        }

        val products = merchantGateway.findOrderableProducts(request.items.map { it.productId }.distinct())
        val productsById = products.associateBy { it.productId }
        validateProductsForMerchant(request, productsById)
        requireActiveBranch(request.merchantId, request.branchId)
        validateOfferingRules(request.merchantId, request.orderType, request.usesDeferredPayment)

        val resolvedBuyerContact = request.targetShopperId?.let { shopperId ->
            shopperProfileGateway.findByShopperId(shopperId)?.let { p ->
                BuyerContactRequest(name = p.name, street = p.street, zip = p.zip, city = p.city, country = p.country)
            }
        }

        val order = Order(
            orderId = Order.nextOrderId(),
            merchantApplicationId = request.merchantId,
            merchantBranchId = request.branchId,
            orderType = request.orderType,
            usesDeferredPayment = request.usesDeferredPayment,
            targetShopperId = request.targetShopperId,
            dueDate = request.dueDate,
            creatorType = CreatorType.MERCHANT,
            createdById = merchantUserId,
            currency = DEFAULT_CURRENCY,
            totalAmountMinor = request.items.sumOf { productsById.getValue(it.productId).unitPriceMinor() * it.quantity },
            itemCount = request.items.sumOf { it.quantity },
            buyerContactName = resolvedBuyerContact?.name,
            buyerContactStreet = resolvedBuyerContact?.street,
            buyerContactZip = resolvedBuyerContact?.zip,
            buyerContactCity = resolvedBuyerContact?.city,
            buyerContactCountry = resolvedBuyerContact?.country,
            pickupNumber = pickupNumberAllocator.next(request.branchId),
        )
        val savedOrder = orderRepository.save(order)
        val savedItems = orderItemRepository.saveAll(
            request.items.map { OrderItem.fromProduct(savedOrder.id, productsById.getValue(it.productId), it.quantity) }
        )
        notifyKitchen(savedOrder, savedItems, KitchenEventType.CREATED)

        val targetShopper = savedOrder.targetShopperId
        if (targetShopper != null) {
            eventPublisher.publishEvent(InvoiceOrderCreated(
                orderId = savedOrder.orderId,
                orderIdFk = savedOrder.id,
                merchantId = savedOrder.merchantApplicationId,
                branchId = savedOrder.merchantBranchId,
                targetShopperId = targetShopper,
                totalAmountMinor = savedOrder.totalAmountMinor,
                currency = savedOrder.currency,
                dueDate = savedOrder.dueDate,
                buyerContactName = savedOrder.buyerContactName,
                buyerContactStreet = savedOrder.buyerContactStreet,
                buyerContactZip = savedOrder.buyerContactZip,
                buyerContactCity = savedOrder.buyerContactCity,
                buyerContactCountry = savedOrder.buyerContactCountry,
                items = savedItems.map { OrderItemSnapshot(it.productName, it.quantity, it.effectiveUnitPriceMinor) },
            ))
            return savedOrder.toMerchantResponse(savedItems)
        }

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
            .findShopperOrders(shopperId, cutoff)
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
        if (product.merchantId != order.merchantApplicationId || (product.branchId != null && product.branchId != order.merchantBranchId)) {
            throw OrderMutationConflictException("Product does not belong to the order's merchant")
        }
        if (order.preparationStatus == PreparationStatus.READY) {
            // Everything on a READY order is already made: lock it and send the order back to the kitchen.
            orderItemRepository.saveAll(orderItemRepository.findAllByOrderIdFk(order.id).onEach { it.locked = true })
            order.preparationStatus = PreparationStatus.RECEIVED
        }
        val newItem = OrderItem.fromProduct(order.id, product, request.quantity)
        orderItemRepository.save(newItem)
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        order.recalculateTotals(items)
        orderRepository.save(order)
        notifyKitchen(order, items, KitchenEventType.CHANGED)
        return order.toResponse(items)
    }

    fun updateItemQuantity(orderId: String, itemId: UUID, request: UpdateOrderItemRequest, actorId: String): OrderResponse {
        require(request.quantity > 0) { "quantity must be > 0" }
        val order = requireMutableOrder(orderId)
        val item = requireOrderItem(order.id, itemId)
        requireItemEditable(order, item)
        item.updateQuantity(request.quantity)
        orderItemRepository.save(item)
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        order.recalculateTotals(items)
        orderRepository.save(order)
        notifyKitchen(order, items, KitchenEventType.CHANGED)
        return order.toResponse(items)
    }

    fun removeItem(orderId: String, itemId: UUID, actorId: String): OrderResponse {
        val order = requireMutableOrder(orderId)
        requireItemEditable(order, requireOrderItem(order.id, itemId))
        val allItems = orderItemRepository.findAllByOrderIdFk(order.id)
        return if (allItems.size <= 1) {
            order.status = OrderStatus.CANCELLED
            order.updatedAt = java.time.Instant.now()
            orderRepository.save(order)
            notifyKitchen(order, allItems, KitchenEventType.CANCELLED)
            order.toResponse(allItems)
        } else {
            orderItemRepository.deleteById(itemId)
            val remaining = allItems.filter { it.id != itemId }
            order.recalculateTotals(remaining)
            if (remaining.all { it.locked || it.preparationStatus == PreparationStatus.READY } && order.preparationStatus != PreparationStatus.READY) {
                order.preparationStatus = PreparationStatus.READY
                val shopper = order.targetShopperId ?: order.createdById.takeIf { order.creatorType == CreatorType.SHOPPER }
                shopper?.let { eventPublisher.publishEvent(com.elegant.software.blitzpay.order.api.OrderReadyForPickup(
                    order.orderId, order.id, order.merchantBranchId, order.pickupNumber, it
                )) }
            }
            orderRepository.save(order)
            notifyKitchen(order, remaining, KitchenEventType.CHANGED)
            order.toResponse(remaining)
        }
    }

    fun cancelOrder(orderId: String, actorId: String): OrderResponse {
        val order = requireMutableOrder(orderId)
        order.status = OrderStatus.CANCELLED
        order.updatedAt = java.time.Instant.now()
        orderRepository.save(order)
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        notifyKitchen(order, items, KitchenEventType.CANCELLED)
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
        storefrontOrderBus.publishAfterCommit(order, items)
        notifyTable(order)
        return order.toResponse(items)
    }

    @Transactional(readOnly = true)
    fun listBranchCustomers(branchId: UUID): List<BranchCustomerResponse> {
        val shopperIds = orderRepository.findDistinctShopperIdsByBranch(branchId)
        return shopperIds.mapNotNull { shopperId ->
            shopperProfileGateway.findByShopperId(shopperId)?.let { profile ->
                BranchCustomerResponse(shopperAccountId = shopperId, name = profile.name)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getMerchantOrder(orderId: String): OrderResponse {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        return order.toResponse(items)
    }

    private fun requireMutableOrder(orderId: String): Order {
        orderRepository.lockForItemMutation(orderId)
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        if (order.status != OrderStatus.CREATED) {
            throw OrderMutationConflictException(
                "Order cannot be mutated in status: ${order.status}"
            )
        }
        return order
    }

    /** Once READY (or after a READY order was re-opened) existing items are already made; only additions are allowed. */
    private fun requireItemEditable(order: Order, item: OrderItem) {
        if (order.preparationStatus == PreparationStatus.READY) {
            throw OrderMutationConflictException("Order is ready for pickup; existing items cannot be changed, only added")
        }
        if (item.locked || item.preparationStatus == PreparationStatus.READY) {
            throw OrderMutationConflictException("Order item is already prepared and cannot be changed: ${item.productName}")
        }
    }

    /** Also pushes guest orders to their storefront stream: item edits can re-open a READY order's Preparation Status. */
    private fun notifyKitchen(order: Order, items: List<OrderItem>, type: KitchenEventType) {
        storefrontOrderBus.publishAfterCommit(order, items)
        notifyTable(order)
        if (order.appointmentId != null) return
        kitchenOrderBus.publishAfterCommit(order.merchantBranchId, type, order.toKitchenOrder(items))
    }

    /** Orders at a Table show on the live floor, so every change to one updates it. */
    private fun notifyTable(order: Order) {
        val tableId = order.tableId ?: return
        val branchId = order.merchantBranchId ?: return
        tableGateway.tableChanged(branchId, tableId)
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

        val merchantIds = productsById.values.map { it.merchantId }.distinct()
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

        val wrongMerchant = productsById.values.filter { it.merchantId != request.merchantId }.map { it.productId }
        if (wrongMerchant.isNotEmpty()) throw OrderCreationConflictException("Products do not belong to merchant: ${wrongMerchant.joinToString(",")}")
        val wrongBranch = productsById.values.filter { it.branchId != request.branchId }.map { it.productId }
        if (wrongBranch.isNotEmpty()) throw OrderCreationConflictException("Products do not belong to branch: ${wrongBranch.joinToString(",")}")
    }

    /**
     * Ordering at a Table: a Walk-In order (geolocation still proves presence, ADR-0023) at an active Table of the
     * ordered Branch. Walk-In Ordering being enabled is checked with the other offering rules.
     */
    private fun requireOrderableTable(tableCode: String, request: CreateOrderRequest): TableInfo {
        require(request.orderType == OrderType.WALK_IN_ORDERING) { "Orders at a Table must be WALK_IN_ORDERING" }
        val table = tableGateway.findTableByCode(tableCode) ?: throw NoSuchElementException("Table not found: $tableCode")
        if (table.branchId != request.branchId) {
            throw OrderCreationConflictException("Table $tableCode does not belong to branch ${request.branchId}")
        }
        if (!table.active) throw OrderCreationConflictException("Table $tableCode is not active")
        return table
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
        if (customerLocation == null) {
            throw ApiException(HttpStatus.BAD_REQUEST, "customerLocation is required for WALK_IN_ORDERING", "LOCATION_REQUIRED")
        }
        if (customerLocation.accuracyMeters !in 0.0..MAX_WALK_IN_ACCURACY_METERS) {
            throw ApiException(HttpStatus.BAD_REQUEST, "customerLocation accuracy is insufficient for WALK_IN_ORDERING", "LOCATION_INACCURATE")
        }
        if (!customerLocation.capturedAt.isAfter(Instant.now().minus(MAX_WALK_IN_LOCATION_AGE))) {
            throw ApiException(HttpStatus.BAD_REQUEST, "customerLocation is too old for WALK_IN_ORDERING", "LOCATION_EXPIRED")
        }
        val location = requireNotNull(branch.location) { "Branch location is required for WALK_IN_ORDERING" }
        val distanceMeters = haversineMeters(
            customerLocation.latitude,
            customerLocation.longitude,
            location.latitude,
            location.longitude,
        )
        if (distanceMeters > location.geofenceRadiusMeters) {
            throw OrderCreationConflictException("Customer is outside the branch geofence", "OUTSIDE_GEOFENCE")
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
        private const val GUEST_CREATOR_ID = "guest"
        private const val MAX_GUEST_ORDER_LINES = 50
        private const val MAX_GUEST_ITEM_QUANTITY = 99
        private const val MAX_GUEST_COUNT = 50
        private val MAX_WALK_IN_LOCATION_AGE = ChronoUnit.MINUTES.duration.multipliedBy(5)
        private const val MAX_WALK_IN_ACCURACY_METERS = 100.0
    }
}

class OrderCreationConflictException(message: String, code: String = HttpStatus.UNPROCESSABLE_ENTITY.name) :
    ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message, code)
class OrderMutationConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)
class UnauthenticatedException(message: String = "Valid authentication is required") : ApiException(HttpStatus.UNAUTHORIZED, message)
