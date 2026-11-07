package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.StorefrontTableResponse
import com.elegant.software.blitzpay.merchant.api.TableAvailabilityResponse
import com.elegant.software.blitzpay.merchant.service.TableFloorService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Anonymous Table Link resolution and Table availability for the web storefront and shopper app. */
@Tag(name = "Storefront Tables", description = "Resolve a Table Link and show how many Tables are free")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/storefront", version = "1")
class StorefrontTableController(
    private val floorService: TableFloorService,
) {

    @Operation(
        summary = "Resolve a Table Code to its Merchant, Branch and Table",
        description = "A deactivated Table returns active=false and no tableId: open the Branch storefront for counter pickup.",
    )
    @GetMapping("/tables/{tableCode}")
    fun resolve(@PathVariable tableCode: String): StorefrontTableResponse = floorService.resolveTableLink(tableCode)

    @Operation(summary = "How many of the Branch's active Tables are free; never includes orders")
    @GetMapping("/branches/{branchId}/table-availability")
    fun availability(@PathVariable branchId: UUID): TableAvailabilityResponse = floorService.availability(branchId)
}
