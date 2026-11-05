package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.FloorPlanImageUploadResponse
import com.elegant.software.blitzpay.merchant.api.FloorPlanResponse
import com.elegant.software.blitzpay.merchant.api.PlaceTableRequest
import com.elegant.software.blitzpay.merchant.api.TableOrderLookup
import com.elegant.software.blitzpay.merchant.api.TablePlacementResponse
import com.elegant.software.blitzpay.merchant.api.TableResponse
import com.elegant.software.blitzpay.merchant.api.UpdateTableRequest
import com.elegant.software.blitzpay.merchant.config.TableProperties
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantFloorPlan
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTable
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantFloorPlanRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantTableRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** A Table cannot be deleted because orders were placed at it; deactivate it instead. */
class TableConflictException(message: String) : RuntimeException(message)

/**
 * Setup of Tables, Floor Plans and Table Placements of a Branch (Merchant Manager or Platform Admin; the caller is
 * authorised by the web/MCP layer). Every method taking a `branchId` only touches Tables and Floor Plans of that Branch.
 */
@Service
@Transactional
class MerchantTableService(
    private val tableRepository: MerchantTableRepository,
    private val floorPlanRepository: MerchantFloorPlanRepository,
    private val tableOrderLookup: TableOrderLookup,
    private val storageService: StorageService,
    private val properties: TableProperties,
) {
    private val log = LoggerFactory.getLogger(MerchantTableService::class.java)

    @Transactional(readOnly = true)
    fun listTables(branchId: UUID): List<TableResponse> =
        tableRepository.findAllByBranchIdOrderByCreatedAtAsc(branchId).map { it.toResponse() }

    fun createTables(branchId: UUID, names: List<String>, seats: Int? = null): List<TableResponse> {
        require(names.isNotEmpty()) { "names must contain at least one Table Name" }
        requireValidSeats(seats)
        require(names.size <= MAX_TABLES_PER_REQUEST) { "At most $MAX_TABLES_PER_REQUEST Tables can be created at once" }
        val cleaned = names.map(::requireTableName)
        val batchCodes = mutableSetOf<String>()
        val tables = cleaned.map { name ->
            val code = TableCodeGenerator.unique { it in batchCodes || tableRepository.existsByCode(it) }
            batchCodes += code
            MerchantTable(branchId = branchId, code = code, name = name, seats = seats)
        }
        val saved = tableRepository.saveAll(tables)
        log.info("tables created branchId={} count={}", branchId, saved.size)
        return saved.map { it.toResponse() }
    }

    fun updateTable(branchId: UUID, tableId: UUID, request: UpdateTableRequest): TableResponse {
        val table = requireTable(branchId, tableId)
        request.name?.let { table.name = requireTableName(it) }
        request.active?.let { table.active = it }
        if (request.seatsProvided) table.seats = requireValidSeats(request.seats)
        table.updatedAt = Instant.now()
        return tableRepository.save(table).toResponse()
    }

    fun placeTable(branchId: UUID, tableId: UUID, request: PlaceTableRequest): TableResponse {
        val table = requireTable(branchId, tableId)
        requireFloorPlan(branchId, request.floorPlanId)
        table.place(request.floorPlanId, request.x, request.y)
        return tableRepository.save(table).toResponse()
    }

    fun unplaceTable(branchId: UUID, tableId: UUID): TableResponse {
        val table = requireTable(branchId, tableId)
        table.unplace()
        return tableRepository.save(table).toResponse()
    }

    /** The merchant app reports writing an NFC tag (now) or forgetting it (null). */
    fun recordNfcTag(branchId: UUID, tableId: UUID, writtenAt: Instant?): TableResponse {
        val table = requireTable(branchId, tableId)
        table.recordNfcTag(writtenAt)
        log.info("table nfc tag {} branchId={} tableId={}", if (writtenAt == null) "cleared" else "written", branchId, tableId)
        return tableRepository.save(table).toResponse()
    }

    fun deleteTable(branchId: UUID, tableId: UUID) {
        val table = requireTable(branchId, tableId)
        if (tableOrderLookup.hasOrders(table.id)) {
            throw TableConflictException("Table ${table.name} has orders and cannot be deleted; deactivate it instead")
        }
        tableRepository.delete(table)
        log.info("table deleted branchId={} tableId={}", branchId, tableId)
    }

    @Transactional(readOnly = true)
    fun listFloorPlans(branchId: UUID): List<FloorPlanResponse> =
        floorPlanRepository.findAllByBranchIdOrderByCreatedAtAsc(branchId).map { it.toResponse() }

    fun createFloorPlan(branchId: UUID, name: String): FloorPlanResponse =
        floorPlanRepository.save(MerchantFloorPlan(branchId = branchId, name = requireFloorPlanName(name))).toResponse()

    fun renameFloorPlan(branchId: UUID, floorPlanId: UUID, name: String): FloorPlanResponse {
        val floorPlan = requireFloorPlan(branchId, floorPlanId)
        floorPlan.name = requireFloorPlanName(name)
        floorPlan.updatedAt = Instant.now()
        return floorPlanRepository.save(floorPlan).toResponse()
    }

    /** Tables placed on the Floor Plan become unplaced; the Tables themselves stay. */
    fun deleteFloorPlan(branchId: UUID, floorPlanId: UUID) {
        val floorPlan = requireFloorPlan(branchId, floorPlanId)
        val placed = tableRepository.findAllByFloorPlanId(floorPlan.id)
        placed.forEach { it.unplace() }
        tableRepository.saveAllAndFlush(placed)
        floorPlanRepository.delete(floorPlan)
        floorPlan.imageStorageKey?.let { key -> runCatching { storageService.delete(key) } }
        log.info("floor plan deleted branchId={} floorPlanId={} unplacedTables={}", branchId, floorPlanId, placed.size)
    }

    /**
     * Presigned upload for the Floor Plan picture, like the merchant logo upload. The storage key is recorded on the
     * Floor Plan right away, so `imageUrl` points at the picture as soon as the client's PUT to `uploadUrl` succeeds.
     */
    fun createFloorPlanImageUploadUrl(merchantId: UUID, branchId: UUID, floorPlanId: UUID, contentType: String): FloorPlanImageUploadResponse {
        val floorPlan = requireFloorPlan(branchId, floorPlanId)
        val extension = IMAGE_EXTENSIONS[contentType.trim().lowercase()]
            ?: throw IllegalArgumentException("Unsupported Floor Plan image content type: $contentType (use image/jpeg, image/png or image/webp)")
        val key = "merchants/$merchantId/branches/$branchId/floor-plans/${floorPlan.id}.$extension"
        val presigned = storageService.presignUpload(key, contentType.trim().lowercase())
        floorPlan.imageStorageKey = presigned.storageKey
        floorPlan.updatedAt = Instant.now()
        floorPlanRepository.save(floorPlan)
        return FloorPlanImageUploadResponse(presigned.storageKey, presigned.uploadUrl, presigned.expiresAt)
    }

    // --- by Table Code (MCP tools) ---

    @Transactional(readOnly = true)
    fun requireTableByCode(tableCode: String): MerchantTable =
        tableRepository.findByCode(TableCodeGenerator.normalize(tableCode))
            ?: throw NoSuchElementException("Table not found: $tableCode")

    @Transactional(readOnly = true)
    fun requireFloorPlanById(floorPlanId: UUID): MerchantFloorPlan =
        floorPlanRepository.findById(floorPlanId).orElseThrow { NoSuchElementException("Floor Plan not found: $floorPlanId") }

    /** Deletes every Table and Floor Plan of the Branches, Tables first (RESTRICT FKs); used when a Merchant is deleted. */
    fun deleteAllForBranches(branchIds: Collection<UUID>) {
        if (branchIds.isEmpty()) return
        tableRepository.deleteAllByBranchIdIn(branchIds)
        floorPlanRepository.deleteAllByBranchIdIn(branchIds)
    }

    fun tableResponse(table: MerchantTable): TableResponse = table.toResponse()

    fun floorPlanResponse(floorPlan: MerchantFloorPlan): FloorPlanResponse = floorPlan.toResponse()

    private fun requireTable(branchId: UUID, tableId: UUID): MerchantTable =
        tableRepository.findById(tableId).orElse(null)?.takeIf { it.branchId == branchId }
            ?: throw NoSuchElementException("Table not found: $tableId")

    private fun requireFloorPlan(branchId: UUID, floorPlanId: UUID): MerchantFloorPlan =
        floorPlanRepository.findById(floorPlanId).orElse(null)?.takeIf { it.branchId == branchId }
            ?: throw NoSuchElementException("Floor Plan not found: $floorPlanId")

    private fun requireValidSeats(seats: Int?): Int? = seats?.also {
        require(it in 1..MAX_SEATS) { "seats must be between 1 and $MAX_SEATS" }
    }

    private fun requireTableName(name: String): String = name.trim().also {
        require(it.isNotEmpty()) { "Table Name must not be blank" }
        require(it.length <= MAX_NAME_LENGTH) { "Table Name must not exceed $MAX_NAME_LENGTH characters" }
    }

    private fun requireFloorPlanName(name: String): String = name.trim().also {
        require(it.isNotEmpty()) { "Floor Plan name must not be blank" }
        require(it.length <= MAX_NAME_LENGTH) { "Floor Plan name must not exceed $MAX_NAME_LENGTH characters" }
    }

    private fun MerchantTable.toResponse() = TableResponse(
        id = id,
        code = code,
        name = name,
        active = active,
        link = "${properties.storefrontUrl.trimEnd('/')}/t/$code",
        placement = floorPlanId?.let { planId ->
            val px = x
            val py = y
            if (px != null && py != null) TablePlacementResponse(planId, px, py) else null
        },
        seats = seats,
        nfcTagWrittenAt = nfcTagWrittenAt,
    )

    private fun MerchantFloorPlan.toResponse() = FloorPlanResponse(
        id = id,
        name = name,
        imageUrl = imageStorageKey?.let { runCatching { storageService.presignDownload(it) }.getOrNull() },
    )

    companion object {
        const val MAX_TABLES_PER_REQUEST = 200
        const val MAX_SEATS = 50
        private const val MAX_NAME_LENGTH = 100
        private val IMAGE_EXTENSIONS = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")
    }
}
