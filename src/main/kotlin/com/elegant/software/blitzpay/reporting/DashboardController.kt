package com.elegant.software.blitzpay.reporting

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@Tag(name = "Dashboard", description = "KPI dashboards for platform admins and merchant app users")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/reporting/dashboard", version = "1")
class DashboardController(private val dashboardQueryService: DashboardQueryService) {

    @Operation(
        summary = "Platform KPI Report",
        description = "Platform-wide KPI snapshot for web admin. Pass merchantId to drill into a single merchant.",
    )
    @GetMapping("/platform")
    fun platform(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) merchantId: UUID?,
    ): ResponseEntity<PlatformKpiReport> {
        require(!from.isAfter(to)) { "from must not be after to" }
        return ResponseEntity.ok(dashboardQueryService.platformKpi(from, to, merchantId))
    }

    @Operation(
        summary = "Merchant KPI Report",
        description = "Merchant or branch KPI snapshot for the merchant app. " +
            "Omit branchId for a merchant-wide view (Merchant Manager); " +
            "supply branchId for a branch-scoped view (Merchant Employee).",
    )
    @GetMapping("/merchant/{merchantId}")
    fun merchant(
        @PathVariable merchantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) branchId: UUID?,
    ): ResponseEntity<MerchantKpiReport> {
        require(!from.isAfter(to)) { "from must not be after to" }
        return ResponseEntity.ok(dashboardQueryService.merchantKpi(merchantId, branchId, from, to))
    }
}
