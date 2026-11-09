package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.config.TableProperties
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTable
import com.elegant.software.blitzpay.support.TestFixtureLoader
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.UUID
import kotlin.test.assertEquals

class TableLinkConfigurationTest {
    private val fixture = TestFixtureLoader.tableLinks()

    @Test
    fun `default Table Link opens the storefront rather than the admin dashboard`() {
        val service = MerchantTableService(mock(), mock(), mock(), mock(), TableProperties())
        val table = MerchantTable(branchId = UUID(0, 1), code = fixture.tableCode, name = "1")
        assertEquals(fixture.localStorefrontUrl + "/t/" + fixture.tableCode, service.tableResponse(table).link)
    }

    @Test
    fun `explicit HTTPS origin retains the existing Table Code`() {
        val service = MerchantTableService(mock(), mock(), mock(), mock(), TableProperties(storefrontUrl = fixture.publicStorefrontUrl + "/"))
        val table = MerchantTable(branchId = UUID(0, 1), code = fixture.tableCode, name = "1")
        assertEquals(fixture.publicStorefrontUrl + "/t/" + fixture.tableCode, service.tableResponse(table).link)
    }

    @Test
    fun `Table Links and guest payment returns share the same configured storefront origin`() {
        val yaml = YamlPropertiesFactoryBean().apply { setResources(ClassPathResource("application.yml")) }.`object`!!
        val expected = "\${STOREFRONT_URL:" + fixture.localStorefrontUrl + "}"
        assertEquals(expected, yaml.getProperty("blitzpay.tables.storefront-url"))
        assertEquals(expected, yaml.getProperty("stripe.storefront-url"))
    }
}
