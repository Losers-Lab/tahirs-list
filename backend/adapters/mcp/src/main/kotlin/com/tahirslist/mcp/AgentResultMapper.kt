package com.tahirslist.mcp

import com.tahirslist.application.agent.AgentListingDetail
import com.tahirslist.application.agent.AgentSearchResult
import com.tahirslist.application.listing.ListingSearchResult
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Maps application results into the structured content the tools return. Field
 * names are the public agent contract (07); nulls are explicit so a model can
 * tell "unknown" from "absent".
 */
object AgentResultMapper {

    fun search(result: AgentSearchResult, config: McpConfig): JsonObject = buildJsonObject {
        put("results", buildJsonArray { result.results.forEach { add(card(it, config)) } })
        put("count", result.count)
        if (result.nextOffset == null) put("nextOffset", JsonNull) else put("nextOffset", result.nextOffset)
    }

    fun detail(detail: AgentListingDetail, config: McpConfig): JsonObject {
        val listing = detail.listing
        return buildJsonObject {
            put("id", listing.id.toString())
            put("name", listing.name)
            put("address", listing.address)
            putNullable("city", listing.city)
            putNullable("province", listing.province)
            putNullable("postal", listing.postal)
            putNullable("country", listing.country)
            put("lat", listing.location.lat)
            put("lng", listing.location.lng)
            putNullable("cuisine", listing.cuisine?.value)
            putNullable("isHandCut", listing.isHandCut)
            putNullable("isDelivery", listing.isDelivery)
            put("halalScope", listing.halalScope.name)
            put(
                "halalItems",
                buildJsonArray {
                    listing.halalItems.sortedBy { it.name }.forEach { item ->
                        add(
                            buildJsonObject {
                                put("name", item.name)
                                put("isHalal", item.isHalal)
                            },
                        )
                    }
                },
            )
            put("crossContamination", listing.crossContamination.name)
            put("verificationStatus", listing.verificationStatus.name)
            put("imageUrl", "${config.apiBase()}/v1/listings/${listing.id}/image?variant=full")
            put("websiteUrl", "${config.webBase()}/restaurants/${listing.id}")
            val certificate = detail.certificate
            if (certificate == null) {
                put("certificate", JsonNull)
            } else {
                put(
                    "certificate",
                    buildJsonObject {
                        putNullable("certifier", certificate.certifier)
                        put("reviewedOn", certificate.reviewedOn.toString())
                        putNullable("expiresOn", certificate.expiresOn?.toString())
                        put(
                            "certificateUrl",
                            "${config.apiBase()}/v1/listings/${listing.id}/certificate",
                        )
                    },
                )
            }
        }
    }

    private fun card(result: ListingSearchResult, config: McpConfig): JsonObject = buildJsonObject {
        put("id", result.id.toString())
        put("name", result.name)
        put("address", result.address)
        putNullable("city", result.city)
        putNullable("province", result.province)
        putNullable("postal", result.postal)
        putNullable("country", result.country)
        put("lat", result.location.lat)
        put("lng", result.location.lng)
        put("distanceMiles", result.distanceMiles)
        putNullable("cuisine", result.cuisine?.value)
        putNullable("isHandCut", result.isHandCut)
        put("halalScope", result.halalScope.name)
        putNullable("isDelivery", result.isDelivery)
        put("verificationStatus", result.verificationStatus.name)
        putNullable("rating", result.rating?.value?.toDouble())
        put("websiteUrl", "${config.webBase()}/restaurants/${result.id}")
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: String?) {
        if (value == null) put(name, JsonNull) else put(name, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: Boolean?) {
        if (value == null) put(name, JsonNull) else put(name, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: Double?) {
        if (value == null) put(name, JsonNull) else put(name, value)
    }
}

/** Trimmed base with no trailing slash, so URL concatenation never doubles it. */
internal fun McpConfig.webBase(): String = publicWebBaseUrl.trimEnd('/')

internal fun McpConfig.apiBase(): String = publicApiBaseUrl.trimEnd('/')
