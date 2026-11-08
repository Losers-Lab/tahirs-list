package com.tahirslist.mcp

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.json.ExperimentalJsonConverter
import io.ktor.utils.io.ByteReadChannel
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Routes MCP JSON-RPC payloads through the SDK's [McpJson] while every other
 * value keeps the REST [Json] contract.
 *
 * The transport calls `call.respond(payload)`; with one application-wide
 * ContentNegotiation, the serializer must be chosen per value/type. MCP needs
 * `explicitNulls = false` and no polymorphic discriminator (strict clients
 * reject explicit `null` optional fields), while REST deliberately emits nulls.
 */
@OptIn(ExperimentalSerializationApi::class)
class McpAwareJsonConverter(
    rest: Json,
    mcp: Json = McpJson,
) : ContentConverter {

    private val restConverter = ExperimentalJsonConverter(rest)
    private val mcpConverter = ExperimentalJsonConverter(mcp)

    override suspend fun serialize(
        contentType: ContentType,
        charset: java.nio.charset.Charset,
        typeInfo: io.ktor.util.reflect.TypeInfo,
        value: Any?,
    ): OutgoingContent? = selectForValue(value)
        .serialize(contentType, charset, typeInfo, value)

    override suspend fun deserialize(
        charset: java.nio.charset.Charset,
        typeInfo: io.ktor.util.reflect.TypeInfo,
        content: ByteReadChannel,
    ): Any? = selectForType(typeInfo)
        .deserialize(charset, typeInfo, content)

    private fun selectForValue(value: Any?): ContentConverter = when {
        value is JSONRPCMessage -> mcpConverter
        value is List<*> && value.all { it is JSONRPCMessage } -> mcpConverter
        else -> restConverter
    }

    private fun selectForType(typeInfo: io.ktor.util.reflect.TypeInfo): ContentConverter {
        val rawName = typeInfo.type.qualifiedName
        val isMcp = (rawName != null && rawName.startsWith(MCP_TYPES_PACKAGE)) ||
            typeInfo.kotlinType?.isMcpType() == true
        return if (isMcp) mcpConverter else restConverter
    }
}

private fun KType.isMcpType(): Boolean {
    val raw = (classifier as? KClass<*>)?.qualifiedName
    if (raw != null && raw.startsWith(MCP_TYPES_PACKAGE)) return true
    return arguments.any { it.type?.isMcpType() == true }
}

private const val MCP_TYPES_PACKAGE = "io.modelcontextprotocol.kotlin.sdk.types"
