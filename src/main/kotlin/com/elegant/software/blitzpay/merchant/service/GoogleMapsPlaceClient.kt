package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.config.GoogleMapsProperties
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

data class PlaceDetails(
    val formattedAddress: String?,
    val rating: Double?,
    val reviewCount: Int?
)

@Component
open class GoogleMapsPlaceClient(
    private val properties: GoogleMapsProperties,
    @Qualifier("googleMapsWebClient")
    private val googleMapsWebClient: WebClient
) {
    open fun fetchPlaceDetails(placeId: String): PlaceDetails {
        require(properties.apiKey.isNotBlank()) { "Google Maps API key is not configured" }
        val body = googleMapsWebClient.get()
            .uri { builder ->
                builder.path("/maps/api/place/details/json")
                    .queryParam("place_id", placeId)
                    .queryParam("fields", "formatted_address,rating,user_ratings_total")
                    .queryParam("key", properties.apiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(JsonNode::class.java)
            .block() ?: error("Google Maps response was empty")

        val status = body.path("status").asText()
        require(status == "OK") { "Google Maps Place Details returned $status" }
        val result = body.path("result")
        return PlaceDetails(
            formattedAddress = result.path("formatted_address").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            rating = result.path("rating").takeIf { !it.isMissingNode && !it.isNull }?.asDouble(),
            reviewCount = result.path("user_ratings_total").takeIf { !it.isMissingNode && !it.isNull }?.asInt()
        )
    }
}
