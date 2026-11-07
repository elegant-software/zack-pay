package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.PreparationConfiguration
import com.elegant.software.blitzpay.merchant.service.PreparationConfigurationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Setup uses the same Merchant Manager / Platform Admin policy as Branch Tables. */
@Tag(name = "Preparation stations", description = "Branch preparation station configuration and catalog routing")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/branches/{branchId}/preparation-configuration", version = "1")
class PreparationConfigurationController(
    private val service: PreparationConfigurationService,
    private val access: TableAccess,
) {
    @Operation(operationId = "getPreparationConfiguration", summary = "Get branch preparation stations and routing")
    @GetMapping
    fun get(@PathVariable merchantId: UUID, @PathVariable branchId: UUID,
            @AuthenticationPrincipal jwt: Jwt?): PreparationConfiguration {
        access.requireSetup(merchantId, branchId, jwt)
        return service.get(branchId)
    }

    @Operation(operationId = "savePreparationConfiguration", summary = "Replace branch preparation stations and routing")
    @PutMapping
    fun save(@PathVariable merchantId: UUID, @PathVariable branchId: UUID,
             @RequestBody request: PreparationConfiguration,
             @AuthenticationPrincipal jwt: Jwt?): PreparationConfiguration {
        access.requireSetup(merchantId, branchId, jwt)
        return service.save(merchantId, branchId, request)
    }
}
