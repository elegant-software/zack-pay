package com.elegant.software.blitzpay.contract

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.iam.repository.MerchantUserAssignmentRepository
import com.elegant.software.blitzpay.merchant.iam.service.UserManagementService
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderPaymentSummary
import com.elegant.software.blitzpay.order.api.PaymentRequestResolver
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import com.elegant.software.blitzpay.order.persistence.repository.PaymentAttemptRepository
import com.elegant.software.blitzpay.QuickpayApplication
import com.elegant.software.blitzpay.merchant.service.MerchantProductCategoryService
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingRepository
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOffering
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantVerticalRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MonitoringRecordRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchAppearanceRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.ProximityEventRepository
import com.elegant.software.blitzpay.appointments.persistence.AppointmentRepository
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.shopper.persistence.ShopperProfileRepository
import com.elegant.software.blitzpay.shopper.service.ShopperProfileService
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchOpeningExceptionRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchOpeningHoursRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeAvailabilityExceptionRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeAvailabilityRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeBranchAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeServiceAssignmentRepository
import com.elegant.software.blitzpay.payments.push.persistence.model.DeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.persistence.model.PaymentStatusRepository
import com.elegant.software.blitzpay.payments.push.persistence.model.ProcessedWebhookEventRepository
import com.elegant.software.blitzpay.payments.push.persistence.model.PushDeliveryAttemptRepository
import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationRepository
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProviderAccountRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantStripeProviderAccountRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.PaymentServiceProviderRepository
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusUpdateGateway
import com.elegant.software.blitzpay.storage.StorageService
import com.elegant.software.blitzpay.support.ContractTestConfig
import com.elegant.software.blitzpay.voice.api.VoiceGateway
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Import
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import com.elegant.software.blitzpay.payments.truelayer.outbound.PaymentService
import com.elegant.software.blitzpay.payments.truelayer.security.JwksService
import com.truelayer.java.TrueLayerClient
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.URI
import java.util.Base64 as JavaBase64

@SpringBootTest(
    classes = [QuickpayApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("contract-test")
@Import(ContractTestConfig::class)
abstract class ContractVerifierBase {

    companion object {
        /** A minimal unsigned JWT whose payload decodes to {"sub":"test-user"}. */
        val fakeJwt: String = run {
            val payload = JavaBase64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"sub":"test-user"}""".toByteArray())
            "fakeheader.$payload.fakesig"
        }
    }

    @LocalServerPort
    private var port: Int = 0

    protected lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var paymentService: PaymentService

    @MockitoBean
    private lateinit var trueLayerClient: TrueLayerClient

    @MockitoBean
    private lateinit var jwksService: JwksService

    @MockitoBean
    protected lateinit var paymentStatusRepository: PaymentStatusRepository

    @MockitoBean
    protected lateinit var deviceRegistrationRepository: DeviceRegistrationRepository

    @MockitoBean
    protected lateinit var processedWebhookEventRepository: ProcessedWebhookEventRepository

    @MockitoBean
    protected lateinit var pushDeliveryAttemptRepository: PushDeliveryAttemptRepository

    @MockitoBean
    protected lateinit var merchantApplicationRepository: MerchantApplicationRepository

    @MockitoBean
    protected lateinit var merchantVerticalRepository: MerchantVerticalRepository

    @MockitoBean
    protected lateinit var merchantBranchRepository: MerchantBranchRepository

    @MockitoBean
    protected lateinit var merchantProductRepository: MerchantProductRepository

    @MockitoBean
    protected lateinit var merchantOfferingRepository: MerchantOfferingRepository

    @MockitoBean
    protected lateinit var merchantOfferingAssignmentRepository: MerchantOfferingAssignmentRepository

    @MockitoBean
    protected lateinit var merchantProductCategoryRepository: MerchantProductCategoryRepository

    @MockitoBean
    protected lateinit var merchantProductCategoryService: MerchantProductCategoryService

    @MockitoBean
    protected lateinit var monitoringRecordRepository: MonitoringRecordRepository

    @MockitoBean
    protected lateinit var merchantBranchAppearanceRepository: MerchantBranchAppearanceRepository

    @MockitoBean
    protected lateinit var proximityEventRepository: ProximityEventRepository

    @MockitoBean
    protected lateinit var storageService: StorageService

    @MockitoBean
    protected lateinit var merchantUserAssignmentRepository: MerchantUserAssignmentRepository

    @MockitoBean
    protected lateinit var userManagementService: UserManagementService

    @MockitoBean
    protected lateinit var voiceGateway: VoiceGateway

    @MockitoBean
    protected lateinit var paymentStatusInitializationGateway: PaymentStatusInitializationGateway

    @MockitoBean
    protected lateinit var paymentStatusUpdateGateway: PaymentStatusUpdateGateway

    @MockitoBean
    protected lateinit var orderGateway: OrderGateway

    @MockitoBean
    protected lateinit var paymentRequestResolver: PaymentRequestResolver

    @MockitoBean
    protected lateinit var orderRepository: OrderRepository

    @MockitoBean
    protected lateinit var orderItemRepository: OrderItemRepository

    @MockitoBean
    protected lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @MockitoBean
    protected lateinit var merchantProviderAccountRepository: MerchantProviderAccountRepository

    @MockitoBean
    protected lateinit var merchantStripeProviderAccountRepository: MerchantStripeProviderAccountRepository

    @MockitoBean
    protected lateinit var paymentServiceProviderRepository: PaymentServiceProviderRepository

    @MockitoBean
    protected lateinit var stripeConnectGateway: StripeConnectGateway

    @MockitoBean
    protected lateinit var entityManager: EntityManager

    @MockitoBean
    protected lateinit var merchantEmployeeRepository: MerchantEmployeeRepository

    @MockitoBean
    protected lateinit var merchantEmployeeBranchAssignmentRepository: MerchantEmployeeBranchAssignmentRepository

    @MockitoBean
    protected lateinit var merchantEmployeeAvailabilityRepository: MerchantEmployeeAvailabilityRepository

    @MockitoBean
    protected lateinit var merchantEmployeeAvailabilityExceptionRepository: MerchantEmployeeAvailabilityExceptionRepository

    @MockitoBean
    protected lateinit var merchantEmployeeServiceAssignmentRepository: MerchantEmployeeServiceAssignmentRepository

    @MockitoBean
    protected lateinit var merchantBranchOpeningHoursRepository: MerchantBranchOpeningHoursRepository

    @MockitoBean
    protected lateinit var merchantBranchOpeningExceptionRepository: MerchantBranchOpeningExceptionRepository

    @MockitoBean
    protected lateinit var appointmentRepository: AppointmentRepository

    @MockitoBean
    protected lateinit var shopperDeviceRegistrationRepository: ShopperDeviceRegistrationRepository

    @MockitoBean
    protected lateinit var orderInvoiceRepository: OrderInvoiceRepository

    @MockitoBean
    protected lateinit var shopperProfileRepository: ShopperProfileRepository

    @MockitoBean
    protected lateinit var shopperProfileService: ShopperProfileService

    @BeforeEach
    fun setupRestAssured() {
        whenever(merchantVerticalRepository.existsByCodeAndActiveTrue(any())).thenReturn(true)
        whenever(merchantOfferingRepository.findAllByActiveTrueOrderByDisplayNameAsc()).thenReturn(
            listOf(
                MerchantOffering("PRE_ORDER", "Pre-Order"),
                MerchantOffering("WALK_IN_ORDERING", "Walk-In Ordering"),
                MerchantOffering("DEFERRED_PAYMENT", "Deferred Payment"),
                MerchantOffering("APPOINTMENT_BOOKING", "Appointment Booking"),
            )
        )
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantApplicationId(any())).thenReturn(emptyList())
        whenever(merchantProviderAccountRepository.findAllByMerchantApplicationId(any())).thenReturn(emptyList())
        whenever(merchantProviderAccountRepository.existsByMerchantApplicationIdAndPspCodeAndEnabledTrue(any(), any())).thenReturn(false)
        doNothing().whenever(paymentStatusInitializationGateway).initialize(any(), any(), any(), any(), any())
        doNothing().whenever(paymentStatusUpdateGateway).settle(any(), any(), any())
        doNothing().whenever(paymentStatusUpdateGateway).fail(any(), any(), any())
        whenever(orderGateway.assertPayable(any())).thenReturn(
            OrderPaymentSummary(
                orderId = "ORDER-123",
                merchantId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                branchId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
                totalAmountMinor = 1099,
                currency = "EUR"
            )
        )
        whenever(stripeConnectGateway.findConnectedAccountId(any())).thenReturn("acct_test_contract")
        whenever(stripeConnectGateway.isChargesEnabled(any())).thenReturn(true)
        whenever(stripeConnectGateway.isDetailsSubmitted(any())).thenReturn(true)
        whenever(stripeConnectGateway.isPayByBankEnabled(any())).thenReturn(true)
        whenever(stripeConnectGateway.getDetails(any())).thenReturn(
            MerchantDetailsResponse(
                applicationId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                applicationReference = "REF-001",
                merchantName = "Test Merchant",
                registrationNumber = "12345678",
                businessType = "RESTAURANT",
                operatingCountry = "GB",
                legalBusinessName = "Test Merchant Ltd",
                primaryBusinessAddress = "1 Test Street, London",
                contactFullName = "John Doe",
                contactEmail = "john@testmerchant.com",
                contactPhoneNumber = "+441234567890",
                status = MerchantOnboardingStatus.ACTIVE,
                submittedAt = null,
                lastUpdatedAt = java.time.Instant.EPOCH,
            )
        )
        whenever(orderGateway.linkPaymentAttempt(any(), any(), any(), anyOrNull())).thenReturn(
            OrderPaymentSummary(
                orderId = "ORDER-123",
                merchantId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                branchId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
                totalAmountMinor = 1099,
                currency = "EUR"
            )
        )
        whenever(orderGateway.isShopperOrder(any(), any())).thenReturn(false)
        whenever(paymentService.startPayment(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<PaymentRequested>(0)
            PaymentResult(
                paymentRequestId = requireNotNull(request.paymentRequestId),
                orderId = request.orderId,
                paymentId = "contract-test-payment-id",
                resourceToken = "contract-test-resource-token",
                redirectReturnUri = request.redirectReturnUri,
                redirectURI = URI.create("https://contract-test.blitzpay.local/payments/${request.paymentRequestId}")
            )
        }
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .defaultHeader("Authorization", "Bearer $fakeJwt")
            .build()
    }
}
