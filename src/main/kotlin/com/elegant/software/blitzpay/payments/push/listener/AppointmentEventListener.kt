package com.elegant.software.blitzpay.payments.push.listener

import com.elegant.software.blitzpay.appointments.api.AppointmentBooked
import com.elegant.software.blitzpay.appointments.api.AppointmentCancelled
import com.elegant.software.blitzpay.appointments.api.AppointmentReminderDue
import com.elegant.software.blitzpay.appointments.api.AppointmentRescheduled
import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.service.ExpoMessage
import com.elegant.software.blitzpay.payments.push.service.ExpoPushClient
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class AppointmentEventListener(
    private val shopperDeviceRepo: ShopperDeviceRegistrationRepository,
    private val expoPushClient: ExpoPushClient,
) {
    private val log = LoggerFactory.getLogger(AppointmentEventListener::class.java)
    private val timeFmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").withZone(ZoneId.of("UTC"))

    @ApplicationModuleListener
    fun on(event: AppointmentBooked) {
        val accountId = event.shopperAccountId ?: return
        send(accountId,
            title = "Appointment Confirmed",
            body = "${event.productName} with ${event.employeeName} on ${timeFmt.format(event.startTime)}"
        )
    }

    @ApplicationModuleListener
    fun on(event: AppointmentCancelled) {
        val accountId = event.shopperAccountId ?: return
        send(accountId,
            title = "Appointment Cancelled",
            body = "${event.productName} with ${event.employeeName} has been cancelled"
        )
    }

    @ApplicationModuleListener
    fun on(event: AppointmentRescheduled) {
        val accountId = event.shopperAccountId ?: return
        send(accountId,
            title = "Appointment Rescheduled",
            body = "${event.productName} with ${event.employeeName} — new time: ${timeFmt.format(event.newStartTime)}"
        )
    }

    @ApplicationModuleListener
    fun on(event: AppointmentReminderDue) {
        send(event.shopperAccountId,
            title = "Upcoming Appointment",
            body = "Reminder: ${event.productName} at ${timeFmt.format(event.startTime)}"
        )
    }

    private fun send(shopperAccountId: String, title: String, body: String) {
        val tokens = shopperDeviceRepo.findAllByShopperAccountIdAndInvalidFalse(shopperAccountId)
            .map { it.expoPushToken }
        if (tokens.isEmpty()) return

        runCatching {
            expoPushClient.send(tokens.map { token ->
                ExpoMessage(to = token, title = title, body = body)
            })
        }.onFailure { ex ->
            log.warn("Failed to send appointment push to shopper {}: {}", shopperAccountId, ex.message)
        }
    }
}
