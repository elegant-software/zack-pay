package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MenuLanguageResponse
import com.elegant.software.blitzpay.merchant.service.MenuLanguageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Merchant Menu Languages", description = "Platform-managed languages a merchant catalogue can be offered in")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/menu-languages", version = "1")
class MerchantMenuLanguageController(private val menuLanguageService: MenuLanguageService) {

    @Operation(summary = "List the Menu Language Catalogue")
    @GetMapping
    fun list(): ResponseEntity<List<MenuLanguageResponse>> =
        ResponseEntity.ok(menuLanguageService.catalogue())
}
