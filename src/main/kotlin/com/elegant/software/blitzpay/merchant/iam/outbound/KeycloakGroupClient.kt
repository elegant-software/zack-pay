package com.elegant.software.blitzpay.merchant.iam.outbound

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import org.springframework.web.service.annotation.PutExchange

@HttpExchange("/groups")
interface KeycloakGroupClient {

    @GetExchange
    fun searchGroups(
        @RequestParam search: String,
        @RequestParam exact: Boolean,
    ): List<KeycloakGroupRepresentation>

    @PostExchange
    fun createGroup(
        @RequestBody body: KeycloakGroupBody,
    ): ResponseEntity<Void>

    @GetExchange("/{parentId}/children")
    fun getChildren(
        @PathVariable parentId: String,
        @RequestParam search: String,
        @RequestParam exact: Boolean,
    ): List<KeycloakGroupRepresentation>

    @PostExchange("/{parentId}/children")
    fun createChildGroup(
        @PathVariable parentId: String,
        @RequestBody body: KeycloakGroupBody,
    ): ResponseEntity<Void>

    @PutExchange("/{groupId}")
    fun updateGroup(
        @PathVariable groupId: String,
        @RequestBody body: KeycloakGroupBody,
    )
}
