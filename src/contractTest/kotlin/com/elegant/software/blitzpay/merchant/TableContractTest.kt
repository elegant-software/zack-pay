package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantFloorPlan
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTable
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.elegant.software.blitzpay.storage.PresignedUpload
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.ServerSentEvent
import reactor.test.StepVerifier
import java.time.Duration
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.Base64
import java.util.Optional
import java.util.UUID

/** Tables, Floor Plans, live floor and the public Table endpoints (zack-pay#195 API contract). */
class TableContractTest : ContractVerifierBase() {

    private val merchant = Merchant(merchantName = "Café Elegant", businessType = "RESTAURANT", operatingCountry = "DE")
    private val merchantId = merchant.id
    private val branch = MerchantBranch(
        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        merchantId = merchantId,
        name = "Altstadt",
        capacity = 40,
    )
    private val base get() = "/v1/merchants/$merchantId/branches/${branch.id}"

    private val managerId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val employeeId = UUID.fromString("66666666-6666-6666-6666-666666666666")
    private val adminId = UUID.fromString("77777777-7777-7777-7777-777777777777")

    private fun bearer(sub: UUID, realmRoles: List<String>? = null): String {
        val roles = realmRoles?.joinToString(",", prefix = ""","realm_access":{"roles":[""", postfix = "]}") { "\"$it\"" }.orEmpty()
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$sub"$roles}""".toByteArray())
        return "Bearer fakeheader.$payload.fakesig"
    }

    private val manager = bearer(managerId)
    private val employee = bearer(employeeId)
    private val platformAdmin = bearer(adminId, listOf("blitzpay-admin"))

    private val plan = MerchantFloorPlan(branchId = branch.id, name = "Inside", imageStorageKey = "merchants/x/plan.png")
    private val table = MerchantTable(branchId = branch.id, code = "K7M2QX", name = "5", seats = 4).apply { place(plan.id, 0.25, 0.5) }

    private lateinit var anonymous: WebTestClient

    @BeforeEach
    fun givenBranchTablesAndCallers() {
        anonymous = webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant))
        whenever(merchantBranchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(merchantBranchRepository.existsById(branch.id)).thenReturn(true)
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(managerId)).thenReturn(
            listOf(MerchantUserAssignment(keycloakUserId = managerId, merchantId = merchantId, role = MerchantOperationsRole.MERCHANT_MANAGER))
        )
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(employeeId)).thenReturn(
            listOf(MerchantUserAssignment(keycloakUserId = employeeId, merchantId = merchantId, branchId = branch.id, role = MerchantOperationsRole.MERCHANT_EMPLOYEE))
        )
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(adminId)).thenReturn(emptyList())
        whenever(merchantTableRepository.findById(table.id)).thenReturn(Optional.of(table))
        whenever(merchantTableRepository.findByCode("K7M2QX")).thenReturn(table)
        whenever(merchantTableRepository.findAllByBranchIdOrderByCreatedAtAsc(branch.id)).thenReturn(listOf(table))
        whenever(merchantTableRepository.findAllByBranchIdAndActiveTrue(branch.id)).thenReturn(listOf(table))
        whenever(merchantTableRepository.save(any<MerchantTable>())).thenAnswer { it.arguments[0] }
        whenever(merchantTableRepository.saveAll(any<List<MerchantTable>>())).thenAnswer { it.arguments[0] }
        whenever(merchantFloorPlanRepository.findById(plan.id)).thenReturn(Optional.of(plan))
        whenever(merchantFloorPlanRepository.findAllByBranchIdOrderByCreatedAtAsc(branch.id)).thenReturn(listOf(plan))
        whenever(merchantFloorPlanRepository.save(any<MerchantFloorPlan>())).thenAnswer { it.arguments[0] }
        whenever(storageService.presignDownload(any(), any())).thenReturn("https://s3.example/plan.png")
        whenever(orderRepository.findAllByTableIdInAndCreatedAtAfterAndStatusNot(any(), any(), eq(OrderStatus.CANCELLED))).thenReturn(emptyList())
    }

    private fun orderAtTable(minutesAgo: Long = 10) = Order(
        orderId = "ORD-TABLE00001",
        merchantApplicationId = merchantId,
        merchantBranchId = branch.id,
        orderType = OrderType.WALK_IN_ORDERING,
        creatorType = CreatorType.GUEST,
        createdById = "guest",
        currency = "EUR",
        totalAmountMinor = 1250,
        itemCount = 2,
        createdAt = Instant.now().minusSeconds(minutesAgo * 60),
        pickupNumber = 12,
        tableId = table.id,
        tableName = "5",
    )

    // --- setup ---

    @Test
    fun `a Merchant Manager creates Tables in bulk with seats, codes and links`() {
        webTestClient.post().uri("$base/tables").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"names": ["1", "2"], "seats": 4}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].name").isEqualTo("1")
            .jsonPath("$[0].code").value<String> { require(Regex("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$").matches(it)) { it } }
            .jsonPath("$[0].link").value<String> { require(Regex("^.+/t/[A-Z0-9]{6}$").matches(it)) { it } }
            .jsonPath("$[0].active").isEqualTo(true)
            .jsonPath("$[0].seats").isEqualTo(4)
            .jsonPath("$[0].placement").isEmpty()
    }

    @Test
    fun `a Platform Admin lists Tables with their placement`() {
        webTestClient.get().uri("$base/tables").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo(table.id.toString())
            .jsonPath("$[0].code").isEqualTo("K7M2QX")
            .jsonPath("$[0].placement.floorPlanId").isEqualTo(plan.id.toString())
            .jsonPath("$[0].placement.x").isEqualTo(0.25)
            .jsonPath("$[0].placement.y").isEqualTo(0.5)
    }

    @Test
    fun `employees and other merchants cannot set up Tables`() {
        webTestClient.post().uri("$base/tables").header(HttpHeaders.AUTHORIZATION, employee)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"names": ["1"]}""")
            .exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("$.error").exists()
        webTestClient.get().uri("/v1/merchants/${UUID.randomUUID()}/branches/${branch.id}/tables").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `PATCH renames, deactivates and clears seats but keeps the code`() {
        webTestClient.patch().uri("$base/tables/${table.id}").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"name": "Terrace 5", "active": false, "seats": null}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("K7M2QX")
            .jsonPath("$.name").isEqualTo("Terrace 5")
            .jsonPath("$.active").isEqualTo(false)
            .jsonPath("$.seats").isEmpty()
    }

    @Test
    fun `placement is set with PUT and removed with DELETE`() {
        webTestClient.put().uri("$base/tables/${table.id}/placement").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"floorPlanId": "${plan.id}", "x": 0.9, "y": 0.1}""")
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.placement.x").isEqualTo(0.9)
        webTestClient.put().uri("$base/tables/${table.id}/placement").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"floorPlanId": "${plan.id}", "x": 1.5, "y": 0.1}""")
            .exchange()
            .expectStatus().isBadRequest
        webTestClient.delete().uri("$base/tables/${table.id}/placement").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.placement").isEmpty()
    }

    @Test
    fun `NFC tag status is recorded with PUT, cleared with DELETE and only by setup callers`() {
        webTestClient.put().uri("$base/tables/${table.id}/nfc-tag").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.nfcTagWrittenAt").exists()
        webTestClient.put().uri("$base/tables/${table.id}/nfc-tag").header(HttpHeaders.AUTHORIZATION, employee)
            .exchange()
            .expectStatus().isForbidden
        webTestClient.delete().uri("$base/tables/${table.id}/nfc-tag").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.nfcTagWrittenAt").isEmpty()
    }

    @Test
    fun `a Table with orders cannot be deleted, one without can`() {
        whenever(orderRepository.existsByTableId(table.id)).thenReturn(true)
        webTestClient.delete().uri("$base/tables/${table.id}").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.error").exists()
        verify(merchantTableRepository, never()).delete(any())

        whenever(orderRepository.existsByTableId(table.id)).thenReturn(false)
        webTestClient.delete().uri("$base/tables/${table.id}").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `Floor Plans are listed, created, renamed, deleted and get a presigned image upload`() {
        webTestClient.get().uri("$base/floor-plans").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo(plan.id.toString())
            .jsonPath("$[0].name").isEqualTo("Inside")
            .jsonPath("$[0].imageUrl").isEqualTo("https://s3.example/plan.png")
        webTestClient.post().uri("$base/floor-plans").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"name": "Terrace"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody().jsonPath("$.name").isEqualTo("Terrace").jsonPath("$.id").exists()
        webTestClient.patch().uri("$base/floor-plans/${plan.id}").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"name": "Garden"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo("Garden")

        whenever(storageService.presignUpload(any(), eq("image/png"), any()))
            .thenAnswer { PresignedUpload(it.getArgument(0), "https://s3.example/upload", Instant.parse("2026-11-04T12:15:00Z")) }
        webTestClient.post().uri("$base/floor-plans/${plan.id}/image/upload-url").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"contentType": "image/png"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.uploadUrl").isEqualTo("https://s3.example/upload")
            .jsonPath("$.storageKey").isEqualTo("merchants/$merchantId/branches/${branch.id}/floor-plans/${plan.id}.png")
            .jsonPath("$.expiresAt").exists()

        whenever(merchantTableRepository.findAllByFloorPlanId(plan.id)).thenReturn(emptyList())
        webTestClient.delete().uri("$base/floor-plans/${plan.id}").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isNoContent
    }

    // --- live floor ---

    @Test
    fun `branch staff see the live floor with Table Status, orders, Guest Count and capacity totals`() {
        whenever(orderRepository.findAllByTableIdInAndCreatedAtAfterAndStatusNot(any(), any(), eq(OrderStatus.CANCELLED)))
            .thenReturn(listOf(orderAtTable()))

        webTestClient.get().uri("$base/floor").header(HttpHeaders.AUTHORIZATION, employee)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.floorPlans[0].name").isEqualTo("Inside")
            .jsonPath("$.tables[0].table.code").isEqualTo("K7M2QX")
            .jsonPath("$.tables[0].status").isEqualTo("OCCUPIED")
            .jsonPath("$.tables[0].occupiedSince").exists()
            .jsonPath("$.tables[0].guestCount").isEmpty()
            .jsonPath("$.tables[0].orders[0].orderId").isEqualTo("ORD-TABLE00001")
            .jsonPath("$.tables[0].orders[0].pickupNumber").isEqualTo(12)
            .jsonPath("$.tables[0].orders[0].totalAmountMinor").isEqualTo(1250)
            .jsonPath("$.tables[0].orders[0].currency").isEqualTo("EUR")
            .jsonPath("$.tables[0].orders[0].paymentStatus").isEqualTo("CREATED")
            .jsonPath("$.tables[0].orders[0].preparationStatus").isEqualTo("RECEIVED")
            .jsonPath("$.totalTables").isEqualTo(1)
            .jsonPath("$.occupiedTables").isEqualTo(1)
            .jsonPath("$.seatedCapacity").isEqualTo(4)
            .jsonPath("$.occupiedSeats").isEqualTo(4)
            .jsonPath("$.branchCapacity").isEqualTo(40)
            .jsonPath("$.totalGuests").isEqualTo(0)
    }

    @Test
    fun `a Platform Admin reads the floor but can neither clear a Table nor set guests`() {
        webTestClient.get().uri("$base/floor").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.tables[0].status").isEqualTo("FREE")
        webTestClient.post().uri("$base/tables/${table.id}/clear").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .exchange()
            .expectStatus().isForbidden
        webTestClient.put().uri("$base/tables/${table.id}/guests").header(HttpHeaders.AUTHORIZATION, platformAdmin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"guestCount": 2}""")
            .exchange()
            .expectStatus().isForbidden
        verify(merchantTableRepository, never()).save(any<MerchantTable>())
    }

    @Test
    fun `callers outside the Branch cannot see the floor`() {
        webTestClient.get().uri("$base/floor").exchange().expectStatus().isForbidden
    }

    @Test
    fun `a Platform Admin who also works at the Branch seats guests and clears the Table`() {
        val staffAdmin = bearer(employeeId, listOf("blitzpay-admin"))
        webTestClient.put().uri("$base/tables/${table.id}/guests").header(HttpHeaders.AUTHORIZATION, staffAdmin)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"guestCount": 2}""")
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.guestCount").isEqualTo(2)
        webTestClient.post().uri("$base/tables/${table.id}/clear").header(HttpHeaders.AUTHORIZATION, staffAdmin)
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `branch staff set guests on a free Table, which makes it occupied, and clear it`() {
        webTestClient.put().uri("$base/tables/${table.id}/guests").header(HttpHeaders.AUTHORIZATION, employee)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"guestCount": 3}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("OCCUPIED")
            .jsonPath("$.guestCount").isEqualTo(3)
            .jsonPath("$.table.seats").isEqualTo(4)
        webTestClient.put().uri("$base/tables/${table.id}/guests").header(HttpHeaders.AUTHORIZATION, employee)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"guestCount": 0}""")
            .exchange()
            .expectStatus().isBadRequest

        webTestClient.post().uri("$base/tables/${table.id}/clear").header(HttpHeaders.AUTHORIZATION, employee)
            .exchange()
            .expectStatus().isNoContent
        assert(table.guestCount == null && table.clearedAt != null)
    }

    @Test
    fun `the floor event stream sends a table event when a Table changes and refuses others`() {
        val events = webTestClient.get().uri("$base/floor/events").header(HttpHeaders.AUTHORIZATION, employee)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectHeader().valueEquals("X-Accel-Buffering", "no")
            .returnResult(object : ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .responseBody
            .filter { it.event() != null }

        StepVerifier.create(events)
            .then {
                // The emitter registers when the response starts; give the servlet a moment before changing the Table.
                Thread.sleep(300)
                webTestClient.put().uri("$base/tables/${table.id}/guests").header(HttpHeaders.AUTHORIZATION, employee)
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"guestCount": 2}""")
                    .exchange().expectStatus().isOk
            }
            .assertNext { sse ->
                assert(sse.event() == "table") { "unexpected event ${sse.event()}" }
                assert(sse.id() == table.id.toString()) { "unexpected id ${sse.id()}" }
                assert(sse.data()!!.contains("\"guestCount\":2")) { sse.data()!! }
                assert(sse.data()!!.contains("\"status\":\"OCCUPIED\"")) { sse.data()!! }
            }
            .thenCancel()
            .verify(Duration.ofSeconds(10))

        webTestClient.get().uri("$base/floor/events").accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isForbidden
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
    }

    // --- public ---

    @Test
    fun `anyone resolves a Table Link without signing in`() {
        anonymous.get().uri("/v1/storefront/tables/k7m2qx")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.merchantId").isEqualTo(merchantId.toString())
            .jsonPath("$.merchantName").isEqualTo("Café Elegant")
            .jsonPath("$.branchId").isEqualTo(branch.id.toString())
            .jsonPath("$.branchName").isEqualTo("Altstadt")
            .jsonPath("$.tableId").isEqualTo(table.id.toString())
            .jsonPath("$.tableName").isEqualTo("5")
            .jsonPath("$.active").isEqualTo(true)
            .jsonPath("$.occupied").isEqualTo(false)
            .jsonPath("$.orders").doesNotExist()
        anonymous.get().uri("/v1/storefront/tables/ZZZZZZ")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `a deactivated Table Link opens the Branch without a Table`() {
        val off = MerchantTable(branchId = branch.id, code = "OFF234", name = "9", active = false)
        whenever(merchantTableRepository.findByCode("OFF234")).thenReturn(off)

        anonymous.get().uri("/v1/storefront/tables/OFF234")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.active").isEqualTo(false)
            .jsonPath("$.tableId").isEmpty()
            .jsonPath("$.tableName").isEmpty()
            .jsonPath("$.branchId").isEqualTo(branch.id.toString())
    }

    @Test
    fun `anyone sees how many Tables and seats are free, never orders`() {
        whenever(orderRepository.findAllByTableIdInAndCreatedAtAfterAndStatusNot(any(), any(), eq(OrderStatus.CANCELLED)))
            .thenReturn(listOf(orderAtTable()))

        anonymous.get().uri("/v1/storefront/branches/${branch.id}/table-availability")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.total").isEqualTo(1)
            .jsonPath("$.free").isEqualTo(0)
            .jsonPath("$.freeSeats").isEqualTo(0)
            .jsonPath("$.orders").doesNotExist()
    }

    @Test
    fun `branch capacity is part of the branch API`() {
        whenever(merchantBranchRepository.findAllByMerchantId(merchantId)).thenReturn(listOf(branch))
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)

        webTestClient.get().uri("/v1/merchants/$merchantId/branches/${branch.id}").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.capacity").isEqualTo(40)
    }
}
