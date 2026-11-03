package com.elegant.software.blitzpay.order.web

import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchGroupAccessTest {
    private val branchId = UUID.randomUUID()

    private fun jwt(groups: List<String>?) = Jwt.withTokenValue("t").header("alg", "none").subject("u")
        .apply { if (groups != null) claim("groups", groups) }.build()

    @Test
    fun `members of the branch group by name or full path are allowed`() {
        assertTrue(BranchGroupAccess.isMember(jwt(listOf("branch_$branchId")), branchId))
        assertTrue(BranchGroupAccess.isMember(jwt(listOf("/merchants/merchant_x/branch_$branchId")), branchId))
    }

    @Test
    fun `other branches, merchant-only groups and missing claims are denied`() {
        assertFalse(BranchGroupAccess.isMember(jwt(listOf("branch_${UUID.randomUUID()}", "/merchants/merchant_x")), branchId))
        assertFalse(BranchGroupAccess.isMember(jwt(null), branchId))
        assertFalse(BranchGroupAccess.isMember(null, branchId))
        assertFailsWith<BranchAccessDeniedException> { BranchGroupAccess.require(jwt(emptyList()), branchId) }
    }
}
