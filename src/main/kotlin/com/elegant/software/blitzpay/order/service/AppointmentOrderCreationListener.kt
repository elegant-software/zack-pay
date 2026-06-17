package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.appointments.api.AppointmentCompleted
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.elegant.software.blitzpay.order.persistence.model.unitPriceMinor
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class AppointmentOrderCreationListener(
    private val merchantGateway: MerchantGateway,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
) {
    private val log = LoggerFactory.getLogger(AppointmentOrderCreationListener::class.java)

    @ApplicationModuleListener
    fun on(event: AppointmentCompleted) {
        val enabled = merchantGateway.getEnabledOfferings(event.merchantId)

        if ("PRE_ORDER" !in enabled || "DEFERRED_PAYMENT" !in enabled) {
            log.warn(
                "Appointment order skipped — merchant does not have PRE_ORDER+DEFERRED_PAYMENT enabled merchantId={} enabled={} appointmentId={}",
                event.merchantId, enabled, event.appointmentId,
            )
            return
        }

        val product = merchantGateway.findOrderableProducts(listOf(event.productId))
            .firstOrNull { it.active }

        if (product == null) {
            log.warn(
                "Skipping appointment order — product not found or inactive productId={} appointmentId={}",
                event.productId, event.appointmentId,
            )
            return
        }

        val creatorType = if (event.shopperAccountId != null) CreatorType.SHOPPER else CreatorType.MERCHANT
        val createdById = event.shopperAccountId ?: event.merchantId.toString()

        val order = Order(
            orderId = Order.nextOrderId(),
            merchantApplicationId = event.merchantId,
            merchantBranchId = event.branchId,
            orderType = OrderType.PRE_ORDER,
            usesDeferredPayment = true,
            creatorType = creatorType,
            createdById = createdById,
            currency = DEFAULT_CURRENCY,
            totalAmountMinor = product.unitPriceMinor(),
            itemCount = 1,
            appointmentId = event.appointmentId,
        )
        val savedOrder = orderRepository.save(order)
        orderItemRepository.save(OrderItem.fromProduct(savedOrder.id, product, 1))

        log.info(
            "Appointment Order created orderId={} appointmentId={} merchantId={}",
            savedOrder.orderId, event.appointmentId, event.merchantId,
        )
    }

    private companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}
