package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MerchantSearchRequest
import com.elegant.software.blitzpay.merchant.api.MerchantSearchResponse
import com.elegant.software.blitzpay.merchant.service.MerchantSearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Merchant Search", description = "Full-text and filter-based merchant search")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants")
class MerchantSearchController(
    private val merchantSearchService: MerchantSearchService,
) {

    @Operation(
        summary = "Search merchants",
        description = "Full-text search across merchant, branch, and product fields, with optional enum filters. " +
            "All fields are optional — an empty body returns the 50 most recently updated merchants."
    )
    @PostMapping("/search")
    fun search(@RequestBody(required = false) request: MerchantSearchRequest?): MerchantSearchResponse =
        merchantSearchService.search(request ?: MerchantSearchRequest())
}
