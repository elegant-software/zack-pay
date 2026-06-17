package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.persistence.model.BusinessProfile
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.persistence.model.Person
import com.elegant.software.blitzpay.merchant.persistence.model.PersonRole
import com.elegant.software.blitzpay.merchant.persistence.model.PrimaryContact
import com.elegant.software.blitzpay.merchant.persistence.model.SupportingMaterial
import com.elegant.software.blitzpay.merchant.persistence.model.SupportingMaterialType
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

object MerchantTestFixtureLoader {

    private val objectMapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .addModule(JavaTimeModule())
        .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    val fixture: MerchantFixtureScenario by lazy {
        val resource = requireNotNull(javaClass.classLoader.getResourceAsStream(FIXTURE_PATH)) {
            "Missing fixture file: $FIXTURE_PATH"
        }
        resource.use { objectMapper.readValue(it) }
    }

    fun merchantApplication(
        applicationReference: String = fixture.inputData.applicationReference,
        status: MerchantOnboardingStatus = MerchantOnboardingStatus.DRAFT
    ): MerchantApplication = MerchantApplication(
        applicationReference = applicationReference,
        businessProfile = businessProfile(),
        primaryContact = primaryContact(),
        status = status
    )

    fun merchantApplicationWithDocuments(
        applicationReference: String = fixture.inputData.applicationReference,
        status: MerchantOnboardingStatus = MerchantOnboardingStatus.DRAFT
    ): MerchantApplication = merchantApplication(applicationReference, status).apply {
        addPerson(
            Person(
                fullName = fixture.inputData.beneficialOwner.fullName,
                role = PersonRole.BENEFICIAL_OWNER,
                countryOfResidence = fixture.inputData.beneficialOwner.countryOfResidence,
                ownershipPercentage = fixture.inputData.beneficialOwner.ownershipPercentage
            )
        )
        addSupportingMaterial(
            SupportingMaterial(
                type = SupportingMaterialType.BUSINESS_REGISTRATION,
                fileName = fixture.inputData.businessRegistrationDocument.fileName,
                storageKey = fixture.inputData.businessRegistrationDocument.storageKeyTemplate
                    .replace("{ref}", applicationReference)
            )
        )
    }

    fun businessProfile(): BusinessProfile = fixture.inputData.let {
        BusinessProfile(
            legalBusinessName = it.legalBusinessName,
            businessType = it.businessType,
            registrationNumber = it.registrationNumber,
            operatingCountry = it.operatingCountry,
            primaryBusinessAddress = it.primaryBusinessAddress
        )
    }

    fun primaryContact(): PrimaryContact = fixture.inputData.let {
        PrimaryContact(
            fullName = it.contactFullName,
            email = it.contactEmail,
            phoneNumber = it.contactPhoneNumber
        )
    }

    private const val FIXTURE_PATH = "testdata/merchant/canonical-merchant-application.json"
}

data class MerchantFixtureScenario(
    val scenarioId: String,
    val description: String,
    val domain: String,
    val tags: List<String>,
    val inputData: MerchantInputData,
    val expectations: MerchantExpectations
)

data class MerchantInputData(
    val applicationReference: String,
    val legalBusinessName: String,
    val businessType: String,
    val registrationNumber: String,
    val operatingCountry: String,
    val primaryBusinessAddress: String,
    val contactFullName: String,
    val contactEmail: String,
    val contactPhoneNumber: String,
    val beneficialOwner: BeneficialOwnerData,
    val businessRegistrationDocument: BusinessRegistrationDocumentData
)

data class BeneficialOwnerData(
    val fullName: String,
    val countryOfResidence: String,
    val ownershipPercentage: Int
)

data class BusinessRegistrationDocumentData(
    val fileName: String,
    val storageKeyTemplate: String
)

data class MerchantExpectations(
    val redactedRegistrationNumber: String,
    val redactedEmail: String,
    val redactedPhoneSuffix: String
)
