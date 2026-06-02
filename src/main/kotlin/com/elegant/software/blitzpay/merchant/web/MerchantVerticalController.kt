package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MerchantVerticalResponse
import com.elegant.software.blitzpay.merchant.application.MerchantVerticalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Merchant Verticals", description = "Platform-managed industry verticals available for merchant registration")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/verticals", version = "1")
class MerchantVerticalController(private val merchantVerticalService: MerchantVerticalService) {

    @Operation(summary = "List all active merchant verticals")
    @GetMapping
    fun list(): ResponseEntity<List<MerchantVerticalResponse>> =
        ResponseEntity.ok(merchantVerticalService.listActive())
}
