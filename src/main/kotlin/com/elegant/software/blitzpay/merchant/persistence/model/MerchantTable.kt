package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A seating place within a Branch. [code] is the permanent Table Code inside the Table Link (ADR-0023);
 * [name] is the editable Table Name. A Table Placement is [floorPlanId] plus [x]/[y] as fractions (0..1) of the picture.
 */
@Entity
@Table(name = "merchant_tables", schema = "blitzpay")
class MerchantTable(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @Column(nullable = false, updatable = false, length = 6)
    val code: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "floor_plan_id")
    var floorPlanId: UUID? = null,

    @Column(name = "x")
    var x: Double? = null,

    @Column(name = "y")
    var y: Double? = null,

    /** Table Seats: guests the Table is set up for (1..50); optional. */
    @Column(name = "seats")
    var seats: Int? = null,

    /** Guest Count of the current sitting; only meaningful while the Table is Occupied (see TableOccupancy). */
    @Column(name = "guest_count")
    var guestCount: Int? = null,

    @Column(name = "guests_set_at")
    var guestsSetAt: Instant? = null,

    @Column(name = "cleared_at")
    var clearedAt: Instant? = null,

    /** When an NFC tag carrying the Table Link was last written; null when none was. */
    @Column(name = "nfc_tag_written_at")
    var nfcTagWrittenAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    fun place(floorPlanId: UUID, x: Double, y: Double, at: Instant = Instant.now()) {
        require(x in 0.0..1.0 && y in 0.0..1.0) { "x and y must be between 0 and 1" }
        this.floorPlanId = floorPlanId
        this.x = x
        this.y = y
        updatedAt = at
    }

    fun unplace(at: Instant = Instant.now()) {
        floorPlanId = null
        x = null
        y = null
        updatedAt = at
    }

    /** Clearing frees the Table and forgets its Guest Count. */
    fun clear(at: Instant = Instant.now()) {
        clearedAt = at
        guestCount = null
        guestsSetAt = null
        updatedAt = at
    }

    /** [at] null forgets the tag, for example after it was lost or replaced by a QR code only. */
    fun recordNfcTag(at: Instant?) {
        nfcTagWrittenAt = at
        updatedAt = Instant.now()
    }

    fun seatGuests(count: Int, at: Instant = Instant.now()) {
        require(count in 1..50) { "guestCount must be between 1 and 50" }
        guestCount = count
        guestsSetAt = at
        updatedAt = at
    }
}
