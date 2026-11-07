package com.elegant.software.blitzpay.payments.push.web

import com.elegant.software.blitzpay.payments.push.service.DeviceRegistrationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Device Registration", description = "Expo push token registration for payment status notifications")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/devices", version = "1")
class DeviceRegistrationController(
    private val deviceRegistrationService: DeviceRegistrationService,
) {
    @Operation(summary = "Register or refresh a push token against an order.")
    @PostMapping
    fun register(@Valid @RequestBody request: DeviceRegistrationRequest): ResponseEntity<DeviceRegistrationResponse> {
        val outcome = deviceRegistrationService.register(request)
        val status = if (outcome.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(outcome.response)
    }

    @Operation(summary = "Explicit unregister (user logout / privacy).")
    @DeleteMapping("/{expoPushToken}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unregister(@PathVariable expoPushToken: String) {
        deviceRegistrationService.unregister(expoPushToken)
    }
}
