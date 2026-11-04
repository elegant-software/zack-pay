package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.PlaceTableRequest
import com.elegant.software.blitzpay.merchant.api.TableOrderLookup
import com.elegant.software.blitzpay.merchant.api.UpdateTableRequest
import com.elegant.software.blitzpay.merchant.config.TableProperties
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantFloorPlan
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTable
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantFloorPlanRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantTableRepository
import com.elegant.software.blitzpay.storage.PresignedUpload
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MerchantTableServiceTest {
    private val tableRepository = mock<MerchantTableRepository>()
    private val floorPlanRepository = mock<MerchantFloorPlanRepository>()
    private val tableOrderLookup = mock<TableOrderLookup>()
    private val storageService = mock<StorageService>()
    private val service = MerchantTableService(
        tableRepository, floorPlanRepository, tableOrderLookup, storageService,
        TableProperties(storefrontUrl = "https://shop.example/"),
    )

    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    init {
        whenever(tableRepository.saveAll(any<List<MerchantTable>>())).thenAnswer { it.arguments[0] }
        whenever(tableRepository.save(any<MerchantTable>())).thenAnswer { it.arguments[0] }
        whenever(floorPlanRepository.save(any<MerchantFloorPlan>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `createTables gives every Table a distinct permanent code and link`() {
        val tables = service.createTables(branchId, listOf(" 1 ", "2", "Terrace 3"))

        assertEquals(listOf("1", "2", "Terrace 3"), tables.map { it.name })
        assertEquals(3, tables.map { it.code }.toSet().size)
        tables.forEach {
            assertTrue(TableCodeGenerator.isWellFormed(it.code))
            assertEquals("https://shop.example/t/${it.code}", it.link)
            assertTrue(it.active)
            assertNull(it.placement)
        }
    }

    @Test
    fun `createTables retries when a generated code is already taken`() {
        var taken = 2
        whenever(tableRepository.existsByCode(any())).thenAnswer { taken-- > 0 }

        val tables = service.createTables(branchId, listOf("1"))

        assertEquals(1, tables.size)
        verify(tableRepository, org.mockito.kotlin.times(3)).existsByCode(any())
    }

    @Test
    fun `createTables rejects blank names and an empty list`() {
        assertFailsWith<IllegalArgumentException> { service.createTables(branchId, emptyList()) }
        assertFailsWith<IllegalArgumentException> { service.createTables(branchId, listOf("1", "  ")) }
        verify(tableRepository, never()).saveAll(any<List<MerchantTable>>())
    }

    @Test
    fun `renaming or deactivating keeps the Table Code`() {
        val table = MerchantTable(branchId = branchId, code = "K7M2QX", name = "5")
        whenever(tableRepository.findById(table.id)).thenReturn(Optional.of(table))

        val updated = service.updateTable(branchId, table.id, UpdateTableRequest.of(name = "Terrace 5", active = false))

        assertEquals("K7M2QX", updated.code)
        assertEquals("Terrace 5", updated.name)
        assertEquals(false, updated.active)
    }

    @Test
    fun `seats are set on create, kept when absent from an update and cleared when sent as null`() {
        val created = service.createTables(branchId, listOf("1"), seats = 4)
        assertEquals(4, created.single().seats)
        assertFailsWith<IllegalArgumentException> { service.createTables(branchId, listOf("1"), seats = 51) }

        val table = MerchantTable(branchId = branchId, code = "K7M2QX", name = "5", seats = 4)
        whenever(tableRepository.findById(table.id)).thenReturn(Optional.of(table))
        assertEquals(4, service.updateTable(branchId, table.id, UpdateTableRequest.of(name = "6")).seats)
        assertEquals(6, service.updateTable(branchId, table.id, UpdateTableRequest.of(seats = 6)).seats)
        assertNull(service.updateTable(branchId, table.id, UpdateTableRequest().apply { seats = null }).seats)
        assertFailsWith<IllegalArgumentException> { service.updateTable(branchId, table.id, UpdateTableRequest.of(seats = 0)) }
    }

    @Test
    fun `a Table of another Branch is not found`() {
        val table = MerchantTable(branchId = UUID.randomUUID(), code = "K7M2QX", name = "5")
        whenever(tableRepository.findById(table.id)).thenReturn(Optional.of(table))

        assertFailsWith<NoSuchElementException> { service.updateTable(branchId, table.id, UpdateTableRequest.of(name = "x")) }
    }

    @Test
    fun `placing needs a Floor Plan of the same Branch and coordinates within the picture`() {
        val table = MerchantTable(branchId = branchId, code = "K7M2QX", name = "5")
        val ownPlan = MerchantFloorPlan(branchId = branchId, name = "Inside")
        val otherPlan = MerchantFloorPlan(branchId = UUID.randomUUID(), name = "Elsewhere")
        whenever(tableRepository.findById(table.id)).thenReturn(Optional.of(table))
        whenever(floorPlanRepository.findById(ownPlan.id)).thenReturn(Optional.of(ownPlan))
        whenever(floorPlanRepository.findById(otherPlan.id)).thenReturn(Optional.of(otherPlan))

        val placed = service.placeTable(branchId, table.id, PlaceTableRequest(ownPlan.id, 0.25, 0.75))
        assertEquals(ownPlan.id, placed.placement?.floorPlanId)
        assertEquals(0.25, placed.placement?.x)

        assertFailsWith<NoSuchElementException> { service.placeTable(branchId, table.id, PlaceTableRequest(otherPlan.id, 0.5, 0.5)) }
        assertFailsWith<IllegalArgumentException> { service.placeTable(branchId, table.id, PlaceTableRequest(ownPlan.id, 1.2, 0.5)) }
    }

    @Test
    fun `a Table with orders cannot be deleted`() {
        val table = MerchantTable(branchId = branchId, code = "K7M2QX", name = "5")
        whenever(tableRepository.findById(table.id)).thenReturn(Optional.of(table))
        whenever(tableOrderLookup.hasOrders(table.id)).thenReturn(true)

        assertFailsWith<TableConflictException> { service.deleteTable(branchId, table.id) }
        verify(tableRepository, never()).delete(any())
    }

    @Test
    fun `a Table without orders is deleted`() {
        val table = MerchantTable(branchId = branchId, code = "K7M2QX", name = "5")
        whenever(tableRepository.findById(table.id)).thenReturn(Optional.of(table))

        service.deleteTable(branchId, table.id)

        verify(tableRepository).delete(table)
    }

    @Test
    fun `deleting a Floor Plan unplaces its Tables first`() {
        val plan = MerchantFloorPlan(branchId = branchId, name = "Terrace")
        val table = MerchantTable(branchId = branchId, code = "K7M2QX", name = "5").apply { place(plan.id, 0.1, 0.2) }
        whenever(floorPlanRepository.findById(plan.id)).thenReturn(Optional.of(plan))
        whenever(tableRepository.findAllByFloorPlanId(plan.id)).thenReturn(listOf(table))

        service.deleteFloorPlan(branchId, plan.id)

        assertNull(table.floorPlanId)
        assertNull(table.x)
        inOrder(tableRepository, floorPlanRepository) {
            verify(tableRepository).saveAllAndFlush(listOf(table))
            verify(floorPlanRepository).delete(plan)
        }
    }

    @Test
    fun `the Floor Plan image upload follows the logo pattern and records the key`() {
        val plan = MerchantFloorPlan(branchId = branchId, name = "Inside")
        whenever(floorPlanRepository.findById(plan.id)).thenReturn(Optional.of(plan))
        val expectedKey = "merchants/$merchantId/branches/$branchId/floor-plans/${plan.id}.png"
        whenever(storageService.presignUpload(eq(expectedKey), eq("image/png"), any()))
            .thenReturn(PresignedUpload(expectedKey, "https://s3.example/upload", Instant.EPOCH))

        val response = service.createFloorPlanImageUploadUrl(merchantId, branchId, plan.id, "image/png")

        assertEquals("https://s3.example/upload", response.uploadUrl)
        assertEquals(expectedKey, plan.imageStorageKey)
        assertFailsWith<IllegalArgumentException> { service.createFloorPlanImageUploadUrl(merchantId, branchId, plan.id, "image/gif") }
    }
}
