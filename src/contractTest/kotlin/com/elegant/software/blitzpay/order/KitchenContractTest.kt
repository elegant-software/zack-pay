package com.elegant.software.blitzpay.order

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.order.api.KitchenEventType
import com.elegant.software.blitzpay.order.api.KitchenOrder
import com.elegant.software.blitzpay.order.api.KitchenOrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.elegant.software.blitzpay.order.persistence.model.PreparationStatus
import com.elegant.software.blitzpay.order.service.KitchenOrderBus
import com.elegant.software.blitzpay.order.service.KitchenService
import com.elegant.software.blitzpay.order.service.OrderMutationConflictException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.test.StepVerifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.codec.ServerSentEvent
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

class KitchenContractTest : ContractVerifierBase() {
    @MockitoBean
    private lateinit var kitchenService: KitchenService

    @Autowired
    private lateinit var kitchenOrderBus: KitchenOrderBus

    @Test
    fun `repeated completion requests return the ready ticket`() {
        whenever(kitchenService.updatePreparationStatus(branchId, orderId, PreparationStatus.READY))
            .thenReturn(kitchenOrder(PreparationStatus.READY))

        repeat(2) {
            webTestClient.post().uri("$base/orders/$orderId/preparation-status")
                .header(HttpHeaders.AUTHORIZATION, member)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"status":"READY"}""")
                .exchange().expectStatus().isOk
                .expectBody()
                .jsonPath("$.preparationStatus").isEqualTo("READY")
                .jsonPath("$.pickupNumber").isEqualTo(42)
        }
    }
    private val branchId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val orderId = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val base = "/v1/merchant/branches/$branchId/kitchen"

    private fun token(vararg groups: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"kitchen-user","groups":[${groups.joinToString(",") { "\"$it\"" }}]}""".toByteArray()
        )
        return "Bearer fakeheader.$payload.fakesig"
    }

    private val member = token("/merchants/merchant_11111111-1111-1111-1111-111111111111/branch_$branchId")

    @Test
    fun `a kiosk of the branch cannot use the kitchen endpoints`() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"kiosk-user","azp":"blitzpay-kiosk","groups":["branch_$branchId"]}""".toByteArray()
        )
        val kiosk = "Bearer fakeheader.$payload.fakesig"

        webTestClient.get().uri("$base/orders").header(HttpHeaders.AUTHORIZATION, kiosk).exchange().expectStatus().isForbidden
        webTestClient.post().uri("$base/orders/$orderId/preparation-status").header(HttpHeaders.AUTHORIZATION, kiosk)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"status":"READY"}""")
            .exchange().expectStatus().isForbidden
    }

    private fun kitchenOrder(status: PreparationStatus = PreparationStatus.RECEIVED, cancelled: Boolean = false) = KitchenOrder(
        orderId = orderId,
        orderCode = "ORD-KITCHEN00001",
        pickupNumber = 42,
        orderType = OrderType.WALK_IN_ORDERING,
        shopperFirstName = "Anna",
        receivedAt = Instant.parse("2026-11-03T09:15:00Z"),
        preparationStatus = status,
        cancelled = cancelled,
        items = listOf(KitchenOrderItem(UUID.fromString("44444444-4444-4444-4444-444444444441"), "Burger", 2, locked = true)),
    )

    @Test
    fun `branding returns the merchant logo url for branch members`() {
        whenever(kitchenService.logoUrl(branchId)).thenReturn("https://cdn.example/logo.png")
        whenever(kitchenService.language(branchId)).thenReturn("de")

        webTestClient.get().uri("$base/branding").header(HttpHeaders.AUTHORIZATION, member)
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.logoUrl").isEqualTo("https://cdn.example/logo.png")
            .jsonPath("$.language").isEqualTo("de")

        webTestClient.get().uri("$base/branding").header(HttpHeaders.AUTHORIZATION, token("/merchants/other"))
            .exchange().expectStatus().isForbidden
    }

    @Test
    fun `list returns the KitchenOrder shape for branch members`() {
        whenever(kitchenService.listOpenOrders(branchId)).thenReturn(listOf(kitchenOrder(), kitchenOrder(cancelled = true)))

        webTestClient.get().uri("$base/orders").header(HttpHeaders.AUTHORIZATION, member)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].orderId").isEqualTo(orderId.toString())
            .jsonPath("$[0].orderCode").isEqualTo("ORD-KITCHEN00001")
            .jsonPath("$[0].pickupNumber").isEqualTo(42)
            .jsonPath("$[0].orderType").isEqualTo("WALK_IN_ORDERING")
            .jsonPath("$[0].shopperFirstName").isEqualTo("Anna")
            .jsonPath("$[0].receivedAt").isEqualTo("2026-11-03T09:15:00Z")
            .jsonPath("$[0].preparationStatus").isEqualTo("RECEIVED")
            .jsonPath("$[0].cancelled").isEqualTo(false)
            .jsonPath("$[0].items[0].itemId").isEqualTo("44444444-4444-4444-4444-444444444441")
            .jsonPath("$[0].items[0].name").isEqualTo("Burger")
            .jsonPath("$[0].items[0].quantity").isEqualTo(2)
            .jsonPath("$[0].items[0].locked").isEqualTo(true)
            .jsonPath("$[1].cancelled").isEqualTo(true)
    }

    @Test
    fun `shopperFirstName is omitted when unknown`() {
        whenever(kitchenService.listOpenOrders(branchId)).thenReturn(listOf(kitchenOrder().copy(shopperFirstName = null)))

        webTestClient.get().uri("$base/orders").header(HttpHeaders.AUTHORIZATION, member)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].shopperFirstName").doesNotExist()
    }

    @Test
    fun `callers outside the branch group are forbidden`() {
        listOf(token("branch_${UUID.randomUUID()}"), token(), "Bearer $fakeJwt").forEach { auth ->
            webTestClient.get().uri("$base/orders").header(HttpHeaders.AUTHORIZATION, auth)
                .exchange().expectStatus().isForbidden
            webTestClient.post().uri("$base/orders/$orderId/preparation-status").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"status":"PREPARING"}""")
                .exchange().expectStatus().isForbidden
            webTestClient.get().uri("$base/stream").header(HttpHeaders.AUTHORIZATION, auth)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isForbidden
        }
    }

    @Test
    fun `preparation status update returns the updated order`() {
        whenever(kitchenService.updatePreparationStatus(branchId, orderId, PreparationStatus.PREPARING))
            .thenReturn(kitchenOrder(PreparationStatus.PREPARING))

        webTestClient.post().uri("$base/orders/$orderId/preparation-status").header(HttpHeaders.AUTHORIZATION, member)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"status":"PREPARING"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.preparationStatus").isEqualTo("PREPARING")
            .jsonPath("$.pickupNumber").isEqualTo(42)
    }

    @Test
    fun `invalid transitions are 409 and unknown statuses are 400`() {
        whenever(kitchenService.updatePreparationStatus(eq(branchId), eq(orderId), eq(PreparationStatus.READY)))
            .thenThrow(OrderMutationConflictException("Preparation status cannot change from RECEIVED to READY"))

        webTestClient.post().uri("$base/orders/$orderId/preparation-status").header(HttpHeaders.AUTHORIZATION, member)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"status":"READY"}""")
            .exchange().expectStatus().isEqualTo(409)
        webTestClient.post().uri("$base/orders/$orderId/preparation-status").header(HttpHeaders.AUTHORIZATION, member)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"status":"COOKING"}""")
            .exchange().expectStatus().isBadRequest
    }

    @Test
    fun `stream delivers named order events with KitchenOrder data`() {
        val events = webTestClient.get().uri("$base/stream").header(HttpHeaders.AUTHORIZATION, member)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(object : ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .responseBody
            .filter { it.event() != null }

        StepVerifier.create(events)
            .then {
                // The emitter registers when the response starts; give the servlet a moment before publishing.
                Thread.sleep(300)
                kitchenOrderBus.emit(branchId, KitchenEventType.CREATED, kitchenOrder())
                kitchenOrderBus.emit(branchId, KitchenEventType.CANCELLED, kitchenOrder(cancelled = true))
            }
            .assertNext { sse ->
                assert(sse.event() == "order.created") { "unexpected event ${sse.event()}" }
                assert(sse.data()!!.contains("\"pickupNumber\":42")) { sse.data()!! }
            }
            .assertNext { sse ->
                assert(sse.event() == "order.cancelled")
                assert(sse.data()!!.contains("\"cancelled\":true"))
            }
            .thenCancel()
            .verify(Duration.ofSeconds(10))
    }

    @Test
    fun `kitchen endpoints allow the Electron app origin on preflight`() {
        webTestClient.options().uri("$base/orders")
            .header(HttpHeaders.ORIGIN, "app://kitchen")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "app://kitchen")
    }

    @Test
    fun `station endpoints preserve order and table references and use station preparation`() {
        val stationId = UUID.fromString("44444444-4444-4444-4444-444444444442")
        val ticket = kitchenOrder().copy(tableName = "Table 7", items = listOf(
            KitchenOrderItem(UUID.randomUUID(), "Cola", 2, false, stationId, "Drinks Bar")
        ))
        whenever(kitchenService.stations(branchId)).thenReturn(listOf(com.elegant.software.blitzpay.merchant.api.PreparationStation(stationId, "Drinks Bar")))
        whenever(kitchenService.listStationOrders(branchId, stationId)).thenReturn(listOf(ticket))
        whenever(kitchenService.updateStationPreparationStatus(branchId, orderId, stationId, PreparationStatus.PREPARING))
            .thenReturn(ticket.copy(preparationStatus = PreparationStatus.PREPARING))
        webTestClient.get().uri("$base/stations").header(HttpHeaders.AUTHORIZATION, member).exchange()
            .expectStatus().isOk.expectBody().jsonPath("$[0].name").isEqualTo("Drinks Bar")
        webTestClient.get().uri("$base/stations/$stationId/orders").header(HttpHeaders.AUTHORIZATION, member).exchange()
            .expectStatus().isOk.expectBody().jsonPath("$[0].pickupNumber").isEqualTo(42)
            .jsonPath("$[0].tableName").isEqualTo("Table 7").jsonPath("$[0].items[0].name").isEqualTo("Cola")
        webTestClient.post().uri("$base/stations/$stationId/orders/$orderId/preparation-status")
            .header(HttpHeaders.AUTHORIZATION, member).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status":"PREPARING"}""").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.preparationStatus").isEqualTo("PREPARING")
    }

    @Test
    fun `station selection snapshots subscriptions and completion reject callers outside the branch`() {
        val stationId = UUID.randomUUID()
        val outsider = token("branch_${UUID.randomUUID()}")
        for (path in listOf("/stations", "/stations/$stationId/orders", "/stations/$stationId/stream")) {
            webTestClient.get().uri(base + path).header(HttpHeaders.AUTHORIZATION, outsider).exchange().expectStatus().isForbidden
        }
        webTestClient.post().uri("$base/stations/$stationId/orders/$orderId/preparation-status")
            .header(HttpHeaders.AUTHORIZATION, outsider).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status":"READY"}""").exchange().expectStatus().isForbidden
    }

    @Test
    fun `station stream filters food out of drinks and clears tickets whose last item was removed`() {
        val food = UUID.fromString("44444444-4444-4444-4444-444444444441")
        val drinks = UUID.fromString("44444444-4444-4444-4444-444444444442")
        val mixed = kitchenOrder().copy(items = listOf(
            KitchenOrderItem(UUID.randomUUID(), "Burger", 2, false, food, "Food Kitchen"),
            KitchenOrderItem(UUID.randomUUID(), "Cola", 2, false, drinks, "Drinks Bar"),
        ))
        val events = webTestClient.get().uri("$base/stations/$drinks/stream").header(HttpHeaders.AUTHORIZATION, member)
            .accept(MediaType.TEXT_EVENT_STREAM).exchange().expectStatus().isOk
            .returnResult(object : ParameterizedTypeReference<ServerSentEvent<String>>() {}).responseBody.filter { it.event() != null }
        StepVerifier.create(events).then {
            Thread.sleep(300)
            kitchenOrderBus.emit(branchId, KitchenEventType.CREATED, mixed)
            kitchenOrderBus.emit(branchId, KitchenEventType.CANCELLED, mixed.copy(items = mixed.items.filter { it.stationId == food }, cancelled = true))
        }.assertNext {
            kotlin.test.assertTrue(it.data()!!.contains("Cola"))
            kotlin.test.assertFalse(it.data()!!.contains("Burger"))
        }.assertNext {
            kotlin.test.assertEquals("order.changed", it.event())
            kotlin.test.assertTrue(it.data()!!.contains("\"items\":[]"))
            kotlin.test.assertTrue(it.data()!!.contains("\"preparationStatus\":\"READY\""))
        }.thenCancel().verify(Duration.ofSeconds(10))
    }
}
