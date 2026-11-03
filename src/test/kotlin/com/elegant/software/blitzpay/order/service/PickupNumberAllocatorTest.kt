package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PickupNumberAllocatorTest {
    private val branchId = UUID.randomUUID()
    private val merchantGateway = mock<MerchantGateway>()
    private val query = mock<Query> {
        on { setParameter(any<String>(), any()) } doReturn it
        on { executeUpdate() } doReturn 1
        on { singleResult } doReturn 5
    }
    private val entityManager = mock<EntityManager> { on { createNativeQuery(any<String>()) } doReturn query }

    // 23:30 UTC on 3 Nov is already 4 Nov in Berlin.
    private val clock = Clock.fixed(Instant.parse("2026-11-03T23:30:00Z"), ZoneOffset.UTC)
    private val allocator = PickupNumberAllocator(entityManager, merchantGateway, clock)

    @Test
    fun `business day falls back to Europe Berlin when the branch has no time zone`() {
        assertEquals(LocalDate.of(2026, 11, 4), allocator.businessDay(branchId))
    }

    @Test
    fun `business day uses the branch time zone when set`() {
        whenever(merchantGateway.findBranchTimezone(branchId)).thenReturn(ZoneId.of("America/New_York"))
        assertEquals(LocalDate.of(2026, 11, 3), allocator.businessDay(branchId))
    }

    @Test
    fun `next upserts the branch-day counter and returns its value`() {
        assertEquals(5, allocator.next(branchId))

        val sql = argumentCaptor<String>()
        verify(entityManager, org.mockito.kotlin.times(2)).createNativeQuery(sql.capture())
        assertTrue(sql.firstValue.contains("ON CONFLICT (branch_id, business_day)"))
        assertTrue(sql.firstValue.contains("last_number + 1"))
        verify(query, org.mockito.kotlin.times(2)).setParameter("day", LocalDate.of(2026, 11, 4))
    }
}
