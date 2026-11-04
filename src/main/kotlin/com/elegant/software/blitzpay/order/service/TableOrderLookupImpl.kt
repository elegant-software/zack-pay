package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.merchant.api.TableOrderLookup
import com.elegant.software.blitzpay.merchant.api.TableOrderSnapshot
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Orders at Tables for the merchant module's Table Status, without merchant depending on order. */
@Service
@Transactional(readOnly = true)
class TableOrderLookupImpl(
    private val orderRepository: OrderRepository,
) : TableOrderLookup {

    override fun findOrdersAtTables(tableIds: Collection<UUID>, placedAfter: Instant): List<TableOrderSnapshot> =
        if (tableIds.isEmpty()) emptyList()
        else orderRepository.findAllByTableIdInAndCreatedAtAfterAndStatusNot(tableIds, placedAfter, OrderStatus.CANCELLED)
            .mapNotNull { order ->
                val tableId = order.tableId ?: return@mapNotNull null
                TableOrderSnapshot(
                    tableId = tableId,
                    orderId = order.orderId,
                    pickupNumber = order.pickupNumber,
                    totalAmountMinor = order.totalAmountMinor,
                    currency = order.currency,
                    paymentStatus = order.status.name,
                    preparationStatus = order.preparationStatus.name,
                    createdAt = order.createdAt,
                )
            }

    override fun hasOrders(tableId: UUID): Boolean = orderRepository.existsByTableId(tableId)
}
