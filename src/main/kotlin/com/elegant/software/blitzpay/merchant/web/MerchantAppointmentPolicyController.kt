package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Appointment Policy", description = "Merchant-level appointment scheduling configuration")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/appointment-policy", version = "1")
class MerchantAppointmentPolicyController(
    private val merchantRepo: MerchantApplicationRepository,
    private val callerResolver: MerchantCallerResolver,
) {

    @GetMapping
    fun get(
        @PathVariable merchantId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<AppointmentPolicyResponse> {
        callerResolver.resolve(auth, merchantId).requireManagerOrEmployee(merchantId)
        val merchant = merchantRepo.findById(merchantId).orElseThrow { NoSuchElementException("Merchant $merchantId not found") }
        return ResponseEntity.ok(
            AppointmentPolicyResponse(
                bookingIntervalMinutes = merchant.bookingIntervalMinutes,
                bookingLeadTimeMinutes = merchant.bookingLeadTimeMinutes,
                bookingHorizonDays = merchant.bookingHorizonDays,
                reminderLeadTimeMinutes = merchant.reminderLeadTimeMinutes,
            )
        )
    }

    @PatchMapping
    fun update(
        @PathVariable merchantId: UUID,
        @Valid @RequestBody req: UpdateAppointmentPolicyRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<AppointmentPolicyResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val merchant = merchantRepo.findById(merchantId).orElseThrow { NoSuchElementException("Merchant $merchantId not found") }

        req.bookingIntervalMinutes?.let { merchant.bookingIntervalMinutes = it }
        req.bookingLeadTimeMinutes?.let { merchant.bookingLeadTimeMinutes = it }
        req.bookingHorizonDays?.let { merchant.bookingHorizonDays = it }
        req.reminderLeadTimeMinutes?.let { merchant.reminderLeadTimeMinutes = it }

        merchantRepo.save(merchant)

        return ResponseEntity.ok(
            AppointmentPolicyResponse(
                bookingIntervalMinutes = merchant.bookingIntervalMinutes,
                bookingLeadTimeMinutes = merchant.bookingLeadTimeMinutes,
                bookingHorizonDays = merchant.bookingHorizonDays,
                reminderLeadTimeMinutes = merchant.reminderLeadTimeMinutes,
            )
        )
    }
}

data class AppointmentPolicyResponse(
    val bookingIntervalMinutes: Int?,
    val bookingLeadTimeMinutes: Int?,
    val bookingHorizonDays: Int?,
    val reminderLeadTimeMinutes: Int?,
)

data class UpdateAppointmentPolicyRequest(
    @field:Positive val bookingIntervalMinutes: Int? = null,
    @field:Min(0) val bookingLeadTimeMinutes: Int? = null,
    @field:Positive val bookingHorizonDays: Int? = null,
    @field:Min(0) val reminderLeadTimeMinutes: Int? = null,
)
