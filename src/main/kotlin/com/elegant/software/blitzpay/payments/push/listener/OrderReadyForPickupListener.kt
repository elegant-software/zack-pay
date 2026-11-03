package com.elegant.software.blitzpay.payments.push.listener

import com.elegant.software.blitzpay.order.api.OrderReadyForPickup
import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.service.ExpoMessage
import com.elegant.software.blitzpay.payments.push.service.ExpoPushClient
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class OrderReadyForPickupListener(
    private val shopperDeviceRepository: ShopperDeviceRegistrationRepository,
    private val pushClient: ExpoPushClient,
) {
    private val log = LoggerFactory.getLogger(OrderReadyForPickupListener::class.java)

    @ApplicationModuleListener
    fun on(event: OrderReadyForPickup) {
        val devices = shopperDeviceRepository.findAllByShopperAccountIdAndInvalidFalse(event.shopperId)
        if (devices.isEmpty()) {
            log.info("no devices for ready order orderId={}", event.orderId)
            return
        }
        val body = "Order ${orderLabel(event)} is ready for pickup"
        val messages = devices.map { device ->
            ExpoMessage(
                to = device.expoPushToken,
                title = "Order ready",
                body = body,
                data = mapOf("orderId" to event.orderId, "type" to "ORDER_READY"),
            )
        }
        try {
            pushClient.send(messages)
            log.info("order ready push sent orderId={} deviceCount={}", event.orderId, devices.size)
        } catch (ex: Exception) {
            log.error("order ready push failed orderId={}", event.orderId, ex)
        }
    }

    companion object {
        /** "#042" (the Pickup Number staff call out); the order code for orders created before Pickup Numbers. */
        fun orderLabel(event: OrderReadyForPickup): String =
            event.pickupNumber?.let { "#%03d".format(it) } ?: event.orderId
    }
}
