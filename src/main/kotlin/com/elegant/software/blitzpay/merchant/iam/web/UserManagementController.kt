package com.elegant.software.blitzpay.merchant.iam.web

import com.elegant.software.blitzpay.merchant.iam.service.UserManagementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "User Management", description = "Manage Platform User assignments to Merchants and Branches")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/user-management", version = "1")
class UserManagementController(private val service: UserManagementService) {

    @Operation(summary = "Search Platform Users by name or email")
    @GetMapping("/users")
    fun searchUsers(@RequestParam q: String): ResponseEntity<List<UserSearchResult>> =
        ResponseEntity.ok(service.searchUsers(q))

    @Operation(summary = "Get all Merchant User Assignments for a Platform User")
    @GetMapping("/users/{userId}/assignments")
    fun getAssignments(@PathVariable userId: UUID): ResponseEntity<List<MerchantUserAssignmentResponse>> =
        ResponseEntity.ok(service.getAssignments(userId))

    @Operation(summary = "Create a Merchant User Assignment")
    @PostMapping("/users/{userId}/assignments")
    fun createAssignment(
        @PathVariable userId: UUID,
        @RequestBody request: CreateAssignmentRequest,
    ): ResponseEntity<MerchantUserAssignmentResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.createAssignment(userId, request))

    @Operation(summary = "Revoke a Merchant User Assignment")
    @DeleteMapping("/users/{userId}/assignments/{assignmentId}")
    fun revokeAssignment(
        @PathVariable userId: UUID,
        @PathVariable assignmentId: UUID,
    ): ResponseEntity<Void> {
        service.revokeAssignment(userId, assignmentId)
        return ResponseEntity.noContent().build()
    }
}
