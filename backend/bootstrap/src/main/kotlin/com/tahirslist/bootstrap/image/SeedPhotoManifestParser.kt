package com.tahirslist.bootstrap.image

import com.tahirslist.application.image.SeedHeroPhoto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Aisha's seed hero-photo manifest
 * (docs/research/seed-photos-2026-08-30.json) into [SeedHeroPhoto] rows for
 * ingest. kotlinx.serialization tree model (replaces Jackson) — tolerant of the
 * research artifact's shape, extracting only the fields backend ingest needs.
 */
class SeedPhotoManifestParser(private val json: Json = Json) {

    /** @param text the raw manifest JSON text (not a path). */
    fun parse(text: String): List<SeedHeroPhoto> {
        val root = json.parseToJsonElement(text).jsonObject
        val seeds = root["seeds"]?.jsonArray ?: error("manifest must contain a 'seeds' array")
        return seeds.map { element ->
            val seed = element.jsonObject
            SeedHeroPhoto(
                name = seed.string("name").orEmpty(),
                city = seed.string("city"),
                addressGiven = seed.string("address_given"),
                heroUrl = seed.string("hero_url").orEmpty(),
            ).also {
                check(it.heroUrl.isNotBlank()) { "manifest row '${it.name}' has no hero_url" }
            }
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
}
