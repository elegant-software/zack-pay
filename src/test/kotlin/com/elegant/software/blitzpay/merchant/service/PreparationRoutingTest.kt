package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.PreparationConfiguration
import com.elegant.software.blitzpay.merchant.api.PreparationStation
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantPreparationConfiguration
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.repository.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class PreparationRoutingTest {
    @Test
    fun `product overrides precede category defaults and unrouted items go to the first station`() {
        val products = mock<MerchantProductRepository>()
        val branches = mock<MerchantBranchRepository>()
        val configs = mock<MerchantPreparationConfigurationRepository>()
        val gateway = MerchantOnboardingService(mock<MerchantRepository>(), products, branches, mock<MerchantOfferingAssignmentRepository>(), configs, mock())
        val merchant = UUID.randomUUID()
        val branch = UUID.randomUUID()
        val category = UUID.randomUUID()
        val food = PreparationStation(UUID.randomUUID(), "Food Kitchen")
        val drinks = PreparationStation(UUID.randomUUID(), "Drinks Bar")
        val product = MerchantProduct(merchantId = merchant, name = "Cola", unitPrice = BigDecimal("2.00"), merchantBranchId = branch, productCategoryId = category)
        whenever(products.findAllById(any())).thenReturn(listOf(product))
        whenever(branches.findAllById(any())).thenReturn(listOf(MerchantBranch(id = branch, merchantId = merchant, name = "Bona-me")))
        val config = MerchantPreparationConfiguration(branch, PreparationConfiguration(listOf(food, drinks), mapOf(category to food.id), mapOf(product.id to drinks.id)))
        whenever(configs.findById(branch)).thenReturn(Optional.of(config))
        val submitted = gateway.findOrderableProducts(listOf(product.id)).single()
        assertEquals(drinks.id, submitted.preparationStationId)
        config.configuration = config.configuration.copy(productOverrides = emptyMap())
        assertEquals(food.id, gateway.findOrderableProducts(listOf(product.id)).single().preparationStationId)
        assertEquals(drinks.id, submitted.preparationStationId)
        config.configuration = config.configuration.copy(categoryDefaults = emptyMap())
        assertEquals(food.id, gateway.findOrderableProducts(listOf(product.id)).single().preparationStationId)
        config.configuration = PreparationConfiguration()
        assertEquals(UUID(0, 0), gateway.findOrderableProducts(listOf(product.id)).single().preparationStationId)
    }
}
