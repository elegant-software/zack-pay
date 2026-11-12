package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.config.KioskPrincipal
import com.elegant.software.blitzpay.merchant.iam.service.KioskService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** What the Kiosk device itself calls: sign in with the Kiosk Login, then read its Branch's Kiosk Settings. */
@Tag(name = "Kiosk", description = "Kiosk sign-in and Kiosk Settings, called by the Kiosk device")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/kiosk", version = "1")
class KioskController(private val service: KioskService) {

    @Operation(summary = "Sign a Kiosk in with the Branch's Kiosk Login; the token is scoped to that Branch")
    @PostMapping("/login")
    fun login(@RequestBody request: KioskLoginRequest): ResponseEntity<KioskSessionResponse> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.login(request.login, request.password))

    @Operation(summary = "Kiosk Settings of the token's Branch")
    @GetMapping("/settings")
    fun settings(@AuthenticationPrincipal kiosk: KioskPrincipal): KioskSettings = service.settings(kiosk.branchId)
}
