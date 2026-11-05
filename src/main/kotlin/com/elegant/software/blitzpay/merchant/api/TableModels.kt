package com.elegant.software.blitzpay.merchant.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant
import java.util.UUID

/** Table Placement: x and y are fractions (0..1) of the Floor Plan picture, so they are independent of screen size. */
data class TablePlacementResponse(
    val floorPlanId: UUID,
    val x: Double,
    val y: Double,
)

/** `link` is the permanent Table Link `${STOREFRONT_URL}/t/{code}` (ADR-0023). */
data class TableResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val active: Boolean,
    val link: String,
    val placement: TablePlacementResponse?,
    /** Table Seats (1..50), or null when not set. */
    val seats: Int? = null,
    /** When an NFC tag carrying [link] was last written for this Table; null when none was. */
    val nfcTagWrittenAt: Instant? = null,
)

data class FloorPlanResponse(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
)

enum class TableStatus { FREE, OCCUPIED, RESERVED }

data class TableOrderSummary(
    val orderId: String,
    val pickupNumber: Int?,
    val totalAmountMinor: Long,
    val currency: String,
    val paymentStatus: String,
    val preparationStatus: String,
    val createdAt: Instant,
)

data class TableLiveResponse(
    val table: TableResponse,
    val status: TableStatus,
    val occupiedSince: Instant?,
    val orders: List<TableOrderSummary>,
    /** Guest Count of the current sitting; null when the Table is free or nobody gave one. */
    val guestCount: Int? = null,
)

/**
 * The live floor of a Branch: its Floor Plans and every active Table with its Table Status, plus capacity totals.
 * `seatedCapacity` (Seated Capacity) is the sum of the active Tables' seats, `occupiedSeats` that of the OCCUPIED ones.
 * Setup UIs warn, but never block, when `seatedCapacity` exceeds `branchCapacity`.
 */
data class FloorResponse(
    val floorPlans: List<FloorPlanResponse>,
    val tables: List<TableLiveResponse>,
    val totalTables: Int,
    val occupiedTables: Int,
    val seatedCapacity: Int,
    val occupiedSeats: Int,
    val branchCapacity: Int?,
    /** Sum of the Guest Counts of the Occupied Tables. */
    val totalGuests: Int,
)

/** What a shopper sees after opening a Table Link. A deactivated Table has `active=false` and no `tableId`. */
data class StorefrontTableResponse(
    val merchantId: UUID,
    val merchantName: String,
    val branchId: UUID,
    val branchName: String,
    val tableId: UUID?,
    val tableName: String?,
    val active: Boolean,
    /** Whether the Table is Occupied right now, so apps ask "How many people?" only at a free Table. No other detail. */
    val occupied: Boolean = false,
)

/** Shopper-facing availability of a Branch's Tables; never includes orders. */
data class TableAvailabilityResponse(
    val total: Int,
    val free: Int,
    /** Sum of the seats of the free Tables (Tables without seats count as 0). */
    val freeSeats: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateTablesRequest(
    val names: List<String> = emptyList(),
    /** Table Seats applied to every created Table; optional. */
    val seats: Int? = null,
)

/**
 * Partial update of a Table. `name`/`active` are kept when absent; `seats` is kept when absent and cleared when
 * sent as null, so the body's keys matter, not just its values.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class UpdateTableRequest {
    var name: String? = null
    var active: Boolean? = null
    var seats: Int? = null
        set(value) {
            field = value
            seatsProvided = true
        }

    @get:JsonIgnore
    var seatsProvided: Boolean = false
        private set

    override fun equals(other: Any?): Boolean =
        other is UpdateTableRequest && name == other.name && active == other.active &&
            seats == other.seats && seatsProvided == other.seatsProvided

    override fun hashCode(): Int = listOf(name, active, seats, seatsProvided).hashCode()

    companion object {
        /** `seats` null means "keep"; use the setter directly to clear. */
        fun of(name: String? = null, active: Boolean? = null, seats: Int? = null): UpdateTableRequest =
            UpdateTableRequest().also { request ->
                request.name = name
                request.active = active
                if (seats != null) request.seats = seats
            }
    }
}

/** Staff set the Guest Count of a Table; on a free Table this makes it Occupied before any order. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SetGuestCountRequest(
    val guestCount: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlaceTableRequest(
    val floorPlanId: UUID,
    val x: Double,
    val y: Double,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateFloorPlanRequest(
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdateFloorPlanRequest(
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FloorPlanImageUploadRequest(
    val contentType: String,
)

data class FloorPlanImageUploadResponse(
    val storageKey: String,
    val uploadUrl: String,
    val expiresAt: Instant,
)

/** MCP input: update one Table, identified by its Table Code; omitted fields are kept. */
data class TableUpdateInput(
    val tableCode: String,
    val name: String? = null,
    val seats: Int? = null,
)

/** MCP input: place one Table, identified by its Table Code, at x/y fractions (0..1) of a Floor Plan picture. */
data class TablePlacementInput(
    val tableCode: String,
    val x: Double,
    val y: Double,
)
