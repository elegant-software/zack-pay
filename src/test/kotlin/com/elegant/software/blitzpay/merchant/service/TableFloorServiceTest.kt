package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.TableOrderLookup
import com.elegant.software.blitzpay.merchant.api.TableOrderSnapshot
import com.elegant.software.blitzpay.merchant.api.TableStatus
import com.elegant.software.blitzpay.merchant.config.TableProperties
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTable
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantFloorPlanRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantTableRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TableFloorServiceTest {
    private val now = Instant.parse("2026-11-04T12:00:00Z")
    private val tableRepository = mock<MerchantTableRepository>()
    private val floorPlanRepository = mock<MerchantFloorPlanRepository>()
    private val branchRepository = mock<MerchantBranchRepository>()
    private val merchantRepository = mock<MerchantRepository>()
    private val tableOrderLookup = mock<TableOrderLookup>()
    private val properties = TableProperties(autoClear = Duration.ofHours(4))
    private val floorBus = mock<TableFloorBus>()
    private val tableService = MerchantTableService(tableRepository, floorPlanRepository, tableOrderLookup, mock(), properties)
    private val service = TableFloorService(
        tableRepository, floorPlanRepository, branchRepository, merchantRepository, tableOrderLookup, tableService,
        floorBus, properties, Clock.fixed(now, ZoneOffset.UTC),
    )

    private val branchId = UUID.randomUUID()
    private val free = MerchantTable(branchId = branchId, code = "AAAAAA", name = "1", seats = 2)
    private val busy = MerchantTable(branchId = branchId, code = "BBBBBB", name = "2", seats = 4)
    private val cleared = MerchantTable(branchId = branchId, code = "CCCCCC", name = "3", clearedAt = now.minusSeconds(60))
    private val seated = MerchantTable(branchId = branchId, code = "EEEEEE", name = "5", seats = 4, guestCount = 3, guestsSetAt = now.minusSeconds(120))
    private val inactive = MerchantTable(branchId = branchId, code = "DDDDDD", name = "4", active = false, seats = 8)

    private fun order(table: MerchantTable, minutesAgo: Long) = TableOrderSnapshot(
        table.id, "ORD-${table.code}-$minutesAgo", 12, 990, "EUR", "CREATED", "PREPARING", now.minus(Duration.ofMinutes(minutesAgo)),
    )

    @Test
    fun `the floor shows active Tables only, with derived status, current orders and capacity totals`() {
        val branch = MerchantBranch(id = branchId, merchantId = UUID.randomUUID(), name = "Altstadt", capacity = 5)
        whenever(branchRepository.findById(branchId)).thenReturn(Optional.of(branch))
        whenever(tableRepository.findAllByBranchIdOrderByCreatedAtAsc(branchId)).thenReturn(listOf(free, busy, cleared, inactive, seated))
        whenever(tableOrderLookup.findOrdersAtTables(any(), eq(now.minus(Duration.ofHours(4)))))
            .thenReturn(listOf(order(busy, 30), order(cleared, 10)))

        val floor = service.floor(branchId)

        assertEquals(listOf("1", "2", "3", "5"), floor.tables.map { it.table.name })
        val byName = floor.tables.associateBy { it.table.name }
        assertEquals(TableStatus.FREE, byName.getValue("1").status)
        assertEquals(TableStatus.OCCUPIED, byName.getValue("2").status)
        assertEquals(now.minus(Duration.ofMinutes(30)), byName.getValue("2").occupiedSince)
        assertEquals(12, byName.getValue("2").orders.single().pickupNumber)
        assertEquals("PREPARING", byName.getValue("2").orders.single().preparationStatus)
        assertEquals(TableStatus.FREE, byName.getValue("3").status)
        assertNull(byName.getValue("3").occupiedSince)
        assertEquals(TableStatus.OCCUPIED, byName.getValue("5").status)
        assertEquals(3, byName.getValue("5").guestCount)
        assertEquals(now.minusSeconds(120), byName.getValue("5").occupiedSince)
        assertEquals(4, floor.totalTables)
        assertEquals(2, floor.occupiedTables)
        assertEquals(10, floor.seatedCapacity)
        assertEquals(8, floor.occupiedSeats)
        assertEquals(5, floor.branchCapacity)
        assertEquals(3, floor.totalGuests)
    }

    @Test
    fun `availability counts free active Tables`() {
        whenever(branchRepository.existsById(branchId)).thenReturn(true)
        whenever(tableRepository.findAllByBranchIdAndActiveTrue(branchId)).thenReturn(listOf(free, busy, cleared))
        whenever(tableOrderLookup.findOrdersAtTables(any(), any())).thenReturn(listOf(order(busy, 5)))

        val availability = service.availability(branchId)

        assertEquals(3, availability.total)
        assertEquals(2, availability.free)
        assertEquals(2, availability.freeSeats)
    }

    @Test
    fun `clearing sets cleared_at to now, forgets the Guest Count and updates live floors`() {
        whenever(tableRepository.findById(seated.id)).thenReturn(Optional.of(seated))
        whenever(tableRepository.save(any<MerchantTable>())).thenAnswer { it.arguments[0] }

        service.clear(branchId, seated.id)

        assertEquals(now, seated.clearedAt)
        assertNull(seated.guestCount)
        assertNull(seated.guestsSetAt)
        verify(tableRepository).save(seated)
        verify(floorBus).publishAfterCommit(eq(branchId), any())
    }

    @Test
    fun `staff setting a Guest Count makes a free Table occupied`() {
        whenever(tableRepository.findById(free.id)).thenReturn(Optional.of(free))
        whenever(tableRepository.save(any<MerchantTable>())).thenAnswer { it.arguments[0] }

        val live = service.setGuestCount(branchId, free.id, 2)

        assertEquals(TableStatus.OCCUPIED, live.status)
        assertEquals(2, live.guestCount)
        assertEquals(now, live.occupiedSince)
        verify(floorBus).publishAfterCommit(eq(branchId), any())
    }

    @Test
    fun `an order's Guest Count is applied only when the Table has none yet`() {
        whenever(tableRepository.findById(free.id)).thenReturn(Optional.of(free))
        whenever(tableRepository.findById(seated.id)).thenReturn(Optional.of(seated))

        service.seatGuestsIfUnset(free.id, 4)
        service.seatGuestsIfUnset(seated.id, 6)

        assertEquals(4, free.guestCount)
        assertEquals(3, seated.guestCount)
        verify(tableRepository).save(free)
        verify(tableRepository, never()).save(seated)
    }

    @Test
    fun `findTableByCode is case-insensitive`() {
        whenever(tableRepository.findByCode("DDDDDD")).thenReturn(inactive)

        val info = service.findTableByCode("dddddd")!!

        assertEquals(inactive.id, info.tableId)
        assertEquals(branchId, info.branchId)
        assertEquals(false, info.active)
    }

    @Test
    fun `a deactivated Table Link opens the Branch storefront without a Table`() {
        val merchant = Merchant(merchantName = "Café Elegant", businessType = "RESTAURANT", operatingCountry = "DE")
        val branch = MerchantBranch(id = branchId, merchantId = merchant.id, name = "Altstadt")
        whenever(tableRepository.findByCode("DDDDDD")).thenReturn(inactive)
        whenever(tableRepository.findByCode("BBBBBB")).thenReturn(busy)
        whenever(branchRepository.findById(branchId)).thenReturn(Optional.of(branch))
        whenever(merchantRepository.findById(merchant.id)).thenReturn(Optional.of(merchant))

        val deactivated = service.resolveTableLink("dddddd")
        assertEquals(false, deactivated.active)
        assertNull(deactivated.tableId)
        assertNull(deactivated.tableName)
        assertEquals("Altstadt", deactivated.branchName)

        whenever(tableOrderLookup.findOrdersAtTables(any(), any())).thenReturn(listOf(order(busy, 5)))
        val active = service.resolveTableLink("BBBBBB")
        assertEquals(true, active.occupied)
        assertEquals(false, deactivated.occupied)
        assertEquals(busy.id, active.tableId)
        assertEquals("2", active.tableName)
        assertEquals("Café Elegant", active.merchantName)
    }
}
