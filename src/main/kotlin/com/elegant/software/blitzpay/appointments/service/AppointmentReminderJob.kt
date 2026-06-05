package com.elegant.software.blitzpay.appointments.service

import com.elegant.software.blitzpay.appointments.api.AppointmentReminderDue
import com.elegant.software.blitzpay.appointments.persistence.AppointmentRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class AppointmentReminderJob(
    private val appointmentRepo: AppointmentRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(AppointmentReminderJob::class.java)

    @Scheduled(fixedDelayString = "\${blitzpay.appointments.reminder-poll-ms:300000}")
    @Transactional
    fun sendDueReminders() {
        val due = appointmentRepo.findDueReminders(Instant.now())
        if (due.isEmpty()) return

        log.info("Processing {} due appointment reminders", due.size)
        for (appointment in due) {
            runCatching {
                eventPublisher.publishEvent(
                    AppointmentReminderDue(
                        appointmentId = appointment.id,
                        shopperAccountId = appointment.shopperAccountId!!,
                        startTime = appointment.startTime,
                        productName = appointment.productSnapshotName,
                    )
                )
                appointment.reminderSent = true
            }.onFailure { ex ->
                log.warn("Failed to publish reminder for appointment {}: {}", appointment.id, ex.message)
            }
        }
    }
}
