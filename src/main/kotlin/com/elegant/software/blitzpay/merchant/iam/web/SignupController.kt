package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.merchant.iam.service.EmailConflictException
import com.elegant.software.blitzpay.merchant.iam.service.SignupService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ShopperSignupRequest(val name: String, val email: String, val password: String)

data class MerchantSignupRequest(
    val ownerName: String,
    val merchantName: String,
    val email: String,
    val password: String,
    val businessType: String,
)

data class ShopperSignupResponse(val userId: UUID)
data class MerchantSignupResponse(val merchantId: UUID)

/** Public (unauthenticated) signup used by the shopper and merchant apps before their first login. */
@Tag(name = "Signup", description = "Self-service account creation for shoppers and merchants")
@RestController
@Profile("!contract-test")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/signup", version = "1")
class SignupController(private val service: SignupService) {

    @Operation(summary = "Create a shopper account")
    @PostMapping("/shopper")
    @ResponseStatus(HttpStatus.CREATED)
    fun shopper(@RequestBody request: ShopperSignupRequest): ShopperSignupResponse {
        validate(request.name, request.email, request.password)
        return ShopperSignupResponse(service.signupShopper(request.name.trim(), normalize(request.email), request.password))
    }

    @Operation(summary = "Create a merchant, its first Branch and the owner's manager account")
    @PostMapping("/merchant")
    @ResponseStatus(HttpStatus.CREATED)
    fun merchant(@RequestBody request: MerchantSignupRequest): MerchantSignupResponse {
        validate(request.ownerName, request.email, request.password)
        require(request.merchantName.isNotBlank()) { "merchantName must not be blank" }
        require(request.businessType.isNotBlank()) { "businessType must not be blank" }
        return MerchantSignupResponse(
            service.signupMerchant(
                request.ownerName.trim(), request.merchantName.trim(), normalize(request.email),
                request.password, request.businessType,
            )
        )
    }

    // The apps read `error` to show a specific message.
    @ExceptionHandler(EmailConflictException::class)
    fun conflict(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "email_conflict"))

    private fun normalize(email: String) = email.trim().lowercase()

    // Same rule the apps enforce; the realm password policy may add more.
    private fun validate(name: String, email: String, password: String) {
        require(name.isNotBlank()) { "name must not be blank" }
        require(EMAIL.matches(email.trim())) { "email is invalid" }
        require(password.length >= 8 && password.any(Char::isUpperCase) && password.any(Char::isDigit)) {
            "password must be at least 8 characters with an uppercase letter and a digit"
        }
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
