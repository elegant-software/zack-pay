package com.elegant.software.blitzpay.reporting

import com.elegant.software.blitzpay.payments.stripe.api.StripeSettlementRefreshGateway
import com.elegant.software.blitzpay.payments.stripe.api.StripeSettlementRefreshResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@Tag(name = "Settlement Reporting", description = "Payment settlement summary and transaction list for merchants and admins")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/reporting/settlements", version = "1")
class SettlementReportingController(
    private val queryService: PaymentSettlementQueryService,
    private val refreshGateway: StripeSettlementRefreshGateway,
) {

    @Operation(summary = "Aggregate settlement summary for a merchant over a date range")
    @GetMapping("/summary")
    fun summary(
        @RequestParam merchantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<SettlementSummaryResponse> {
        require(!from.isAfter(to)) { "from must not be after to" }
        return ResponseEntity.ok(queryService.summary(merchantId, from, to))
    }

    @Operation(summary = "Paginated settlement transaction list for a merchant (admin only)")
    @GetMapping
    fun list(
        @RequestParam merchantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<SettlementPageResponse> {
        require(!from.isAfter(to)) { "from must not be after to" }
        require(size in 1..100) { "size must be between 1 and 100" }
        return ResponseEntity.ok(queryService.list(merchantId, from, to, page, size))
    }

    @Operation(summary = "Refresh settlement data")
    @PostMapping("/refresh")
    fun refresh(@RequestBody request: SettlementRefreshRequest): ResponseEntity<StripeSettlementRefreshResult> {
        require(request.paymentRequestIds.isNotEmpty()) { "paymentRequestIds must not be empty" }
        require(request.paymentRequestIds.size <= 100) { "At most 100 settlements can be refreshed at once" }
        return ResponseEntity.ok(refreshGateway.refresh(request.merchantId, request.paymentRequestIds))
    }
}
