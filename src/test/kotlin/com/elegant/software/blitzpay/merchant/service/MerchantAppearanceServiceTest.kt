package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateAppearanceRequest
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.MerchantBranchAppearance
import com.elegant.software.blitzpay.merchant.domain.MerchantMobility
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchAppearanceRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MerchantAppearanceServiceTest {
    private val appearances = mock<MerchantBranchAppearanceRepository>()
    private val branches = mock<MerchantBranchRepository>()
    private val merchants = mock<MerchantApplicationRepository>()
    private val service = MerchantAppearanceService(appearances, branches, merchants)
    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val branch = MerchantBranch(
        id = branchId,
        merchantApplicationId = merchantId,
        name = "Mobile Branch",
    )

    @Test
    fun `create rejects fixed-location merchant`() {
        whenever(merchants.findById(merchantId)).thenReturn(Optional.of(merchant(MerchantMobility.BRICK_AND_MORTAR)))
        val request = request()

        assertFailsWith<IllegalArgumentException> { service.create(merchantId, branchId, request) }
        verify(appearances, never()).save(any<MerchantBranchAppearance>())
    }

    @Test
    fun `create rejects overlapping branch appearance`() {
        whenever(merchants.findById(merchantId)).thenReturn(Optional.of(merchant(MerchantMobility.MOBILE)))
        whenever(branches.findById(branchId)).thenReturn(Optional.of(branch))
        whenever(appearances.findOverlapping(any(), any(), any())).thenReturn(listOf(mock()))

        assertFailsWith<IllegalArgumentException> { service.create(merchantId, branchId, request()) }
        verify(appearances, never()).save(any<MerchantBranchAppearance>())
    }

    @Test
    fun `create returns branch metadata and active status`() {
        whenever(merchants.findById(merchantId)).thenReturn(Optional.of(merchant(MerchantMobility.MOBILE)))
        whenever(branches.findById(branchId)).thenReturn(Optional.of(branch))
        whenever(appearances.findOverlapping(any(), any(), any())).thenReturn(emptyList())
        whenever(appearances.save(any<MerchantBranchAppearance>())).thenAnswer { it.getArgument(0) }

        val response = service.create(merchantId, branchId, request())

        assertEquals("Mobile Branch", response.branchName)
        assertEquals("Lunch stop", response.name)
    }

    private fun request(): CreateAppearanceRequest {
        val start = Instant.now().plus(1, ChronoUnit.HOURS)
        return CreateAppearanceRequest(52.52, 13.405, start, start.plus(6, ChronoUnit.HOURS), "Lunch stop")
    }

    private fun merchant(mobility: MerchantMobility) = MerchantApplication(
        id = merchantId,
        applicationReference = "BLTZ-APP",
        businessProfile = BusinessProfile("Mobile GmbH", "RETAIL", "REG", "DE", "Berlin"),
        primaryContact = PrimaryContact("Owner", "owner@example.com", "+49123"),
        merchantMobility = mobility,
    )
}
