package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.FloorResponse
import com.elegant.software.blitzpay.merchant.api.StorefrontTableResponse
import com.elegant.software.blitzpay.merchant.api.TableAvailabilityResponse
import com.elegant.software.blitzpay.merchant.api.TableGateway
import com.elegant.software.blitzpay.merchant.api.TableInfo
import com.elegant.software.blitzpay.merchant.api.TableLiveResponse
import com.elegant.software.blitzpay.merchant.api.TableOrderLookup
import com.elegant.software.blitzpay.merchant.api.TableOrderSnapshot
import com.elegant.software.blitzpay.merchant.api.TableOrderSummary
import com.elegant.software.blitzpay.merchant.api.TableStatus
import com.elegant.software.blitzpay.merchant.config.TableProperties
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTable
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantFloorPlanRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantTableRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Live Table Status for staff (Floor Plans and their SSE stream), Guest Counts, shopper-facing availability and
 * Table Link resolution. Also the [TableGateway] the order module uses when an order is placed at a Table.
 */
@Service
@Transactional
class TableFloorService(
    private val tableRepository: MerchantTableRepository,
    private val floorPlanRepository: MerchantFloorPlanRepository,
    private val branchRepository: MerchantBranchRepository,
    private val merchantRepository: MerchantRepository,
    private val tableOrderLookup: TableOrderLookup,
    private val tableService: MerchantTableService,
    private val floorBus: TableFloorBus,
    private val properties: TableProperties,
    private val clock: Clock = Clock.systemUTC(),
) : TableGateway {
    private val log = LoggerFactory.getLogger(TableFloorService::class.java)

    /** Floor Plans and every active Table of the Branch with its Table Status, current orders and totals. */
    @Transactional(readOnly = true)
    fun floor(branchId: UUID): FloorResponse {
        val branch = branchRepository.findById(branchId).orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        val floorPlans = floorPlanRepository.findAllByBranchIdOrderByCreatedAtAsc(branchId).map(tableService::floorPlanResponse)
        val tables = tableRepository.findAllByBranchIdOrderByCreatedAtAsc(branchId).filter { it.active }
        val now = clock.instant()
        val ordersByTable = currentOrders(tables, now)
        val live = tables.map { liveTable(it, ordersByTable[it.id].orEmpty(), now) }
        val occupied = live.filter { it.status == TableStatus.OCCUPIED }
        return FloorResponse(
            floorPlans = floorPlans,
            tables = live,
            totalTables = live.size,
            occupiedTables = occupied.size,
            seatedCapacity = live.sumOf { it.table.seats ?: 0 },
            occupiedSeats = occupied.sumOf { it.table.seats ?: 0 },
            branchCapacity = branch.capacity,
            totalGuests = occupied.sumOf { it.guestCount ?: 0 },
        )
    }

    /** Staff cleared the Table: earlier orders and its Guest Count no longer make it Occupied. */
    fun clear(branchId: UUID, tableId: UUID) {
        val table = requireTable(branchId, tableId)
        table.clear(clock.instant())
        tableRepository.save(table)
        log.info("table cleared branchId={} tableId={}", branchId, tableId)
        publish(branchId, tableId)
    }

    /** Staff set the Guest Count; on a free Table this makes it Occupied (seated, nothing ordered yet). */
    fun setGuestCount(branchId: UUID, tableId: UUID, guestCount: Int): TableLiveResponse {
        val table = requireTable(branchId, tableId)
        require(table.active) { "Table is not active: $tableId" }
        table.seatGuests(guestCount, clock.instant())
        tableRepository.save(table)
        publish(branchId, tableId)
        return liveTable(table)
    }

    @Transactional(readOnly = true)
    fun availability(branchId: UUID): TableAvailabilityResponse {
        if (!branchRepository.existsById(branchId)) throw NoSuchElementException("Branch not found: $branchId")
        val tables = tableRepository.findAllByBranchIdAndActiveTrue(branchId)
        val now = clock.instant()
        val ordersByTable = currentOrders(tables, now)
        val free = tables.filter { occupancy(it, ordersByTable[it.id].orEmpty(), now).status == TableStatus.FREE }
        return TableAvailabilityResponse(total = tables.size, free = free.size, freeSeats = free.sumOf { it.seats ?: 0 })
    }

    /** Resolves a Table Link. A deactivated Table opens its Branch storefront without a Table. */
    @Transactional(readOnly = true)
    fun resolveTableLink(tableCode: String): StorefrontTableResponse {
        val table = tableRepository.findByCode(TableCodeGenerator.normalize(tableCode))
            ?: throw NoSuchElementException("Table not found: $tableCode")
        val branch = branchRepository.findById(table.branchId).orElseThrow { NoSuchElementException("Table not found: $tableCode") }
        val merchant = merchantRepository.findById(branch.merchantId).orElseThrow { NoSuchElementException("Table not found: $tableCode") }
        val occupied = table.active && occupancy(table).status == TableStatus.OCCUPIED
        return StorefrontTableResponse(
            merchantId = merchant.id,
            merchantName = merchant.merchantName,
            branchId = branch.id,
            branchName = branch.name,
            tableId = table.id.takeIf { table.active },
            tableName = table.name.takeIf { table.active },
            active = table.active,
            occupied = occupied,
        )
    }

    // --- TableGateway (order module) ---

    @Transactional(readOnly = true)
    override fun findTableByCode(tableCode: String): TableInfo? =
        tableRepository.findByCode(TableCodeGenerator.normalize(tableCode))
            ?.let { TableInfo(tableId = it.id, branchId = it.branchId, name = it.name, active = it.active) }

    override fun seatGuestsIfUnset(tableId: UUID, guestCount: Int) {
        val table = tableRepository.findById(tableId).orElse(null) ?: return
        if (occupancy(table).guestCount != null) return
        table.seatGuests(guestCount, clock.instant())
        tableRepository.save(table)
    }

    override fun tableChanged(branchId: UUID, tableId: UUID) = publish(branchId, tableId)

    // --- internals ---

    /** After commit, sends the Table's new live state to the Branch's open floor streams (if any). */
    private fun publish(branchId: UUID, tableId: UUID) {
        floorBus.publishAfterCommit(branchId) {
            tableRepository.findById(tableId).orElse(null)?.takeIf { it.active }?.let { liveTable(it) }
        }
    }

    private fun liveTable(table: MerchantTable): TableLiveResponse {
        val now = clock.instant()
        return liveTable(table, currentOrders(listOf(table), now)[table.id].orEmpty(), now)
    }

    private fun liveTable(table: MerchantTable, orders: List<TableOrderSnapshot>, now: Instant): TableLiveResponse {
        val occupancy = occupancy(table, orders, now)
        return TableLiveResponse(
            table = tableService.tableResponse(table),
            status = occupancy.status,
            occupiedSince = occupancy.occupiedSince,
            orders = occupancy.orders.map { it.toSummary() },
            guestCount = occupancy.guestCount,
        )
    }

    private fun occupancy(table: MerchantTable): TableOccupancy.Occupancy {
        val now = clock.instant()
        return occupancy(table, currentOrders(listOf(table), now)[table.id].orEmpty(), now)
    }

    private fun occupancy(table: MerchantTable, orders: List<TableOrderSnapshot>, now: Instant) =
        TableOccupancy.derive(table.clearedAt, orders, now, properties.autoClear, table.guestCount, table.guestsSetAt)

    private fun requireTable(branchId: UUID, tableId: UUID): MerchantTable =
        tableRepository.findById(tableId).orElse(null)?.takeIf { it.branchId == branchId }
            ?: throw NoSuchElementException("Table not found: $tableId")

    /** One query for all Tables: orders inside the auto-clear window, grouped by Table. */
    private fun currentOrders(tables: List<MerchantTable>, now: Instant): Map<UUID, List<TableOrderSnapshot>> =
        if (tables.isEmpty()) emptyMap()
        else tableOrderLookup.findOrdersAtTables(tables.map { it.id }, now.minus(properties.autoClear)).groupBy { it.tableId }

    private fun TableOrderSnapshot.toSummary() = TableOrderSummary(
        orderId = orderId,
        pickupNumber = pickupNumber,
        totalAmountMinor = totalAmountMinor,
        currency = currency,
        paymentStatus = paymentStatus,
        preparationStatus = preparationStatus,
        createdAt = createdAt,
    )
}
