package com.tahirslist.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Declares a property that may legitimately be `null` (a JSON Schema union
 * type). Both tool output schemas use this so the declared contract cannot
 * drift from what `AgentResultMapper` actually emits.
 */
internal fun JsonObjectBuilder.nullable(name: String, type: String) {
    put(
        name,
        buildJsonObject {
            put("type", JsonArray(listOf(JsonPrimitive(type), JsonPrimitive("null"))))
        },
    )
}
