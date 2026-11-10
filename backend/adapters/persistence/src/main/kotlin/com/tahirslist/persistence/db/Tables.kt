package com.tahirslist.persistence.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/**
 * Exposed table objects mirroring the Flyway-managed PostgreSQL schema
 * (`src/main/resources/db/migration`, V1–V21).
 *
 * Two deliberate boundaries:
 *
 *  - **Flyway owns DDL.** These objects are a typed catalogue of the live
 *    columns: they are used for parameter typing (each raw statement binds its
 *    arguments as `column.columnType to value`, so the JDBC driver receives
 *    correctly typed values and NULLs) and as the executable schema reference.
 *    They are never passed to `SchemaUtils`, so the migrations remain the single
 *    source of truth for DDL and DB-side defaults (`gen_random_uuid()`, `now()`).
 *
 *  - **PostGIS stays in SQL.** The `location geography(Point, 4326)` column is
 *    intentionally NOT mapped: it is written as
 *    `ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography` and read back through
 *    `ST_X(location::geometry)` / `ST_Y(location::geometry)` in raw SQL, exactly
 *    as before. The generated helper columns `location_lat_6` / `location_lng_6`
 *    (V6) are deliberately absent too — nothing writes or reads them through the
 *    DSL.
 *
 * Column types follow the migration history: `timestampWithTimeZone()` for every
 * `TIMESTAMPTZ` and `date()` for `DATE` (exposed-java-time), so Kotlin/JDBC-side
 * values stay `java.time.OffsetDateTime` / `java.time.LocalDate` and are
 * converted at the repository edge.
 *
 * Foreign keys and the composite/partial indexes declared in SQL are documented
 * per table; unique constraints that are part of an *application* contract
 * (users.email, brands.name) are additionally mirrored as `uniqueIndex()` so the
 * typed catalogue carries them.
 */

/** `V1__create_users.sql` — accounts. `id` defaults to `gen_random_uuid()`. */
object Users : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255).uniqueIndex("uk_users_email")
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 32)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * `V2__create_refresh_tokens.sql` + `V3__add_refresh_token_families.sql`.
 *
 * `token_hash` is the SHA-256 hex of the opaque token (the plaintext is never
 * persisted). `family_id` links a login's rotation lineage; `consumed_at` is the
 * soft-consume marker (`NULL` = live) that makes reuse detection possible.
 * FK: `account_id -> users(id) ON DELETE CASCADE`.
 */
object RefreshTokens : Table("refresh_tokens") {
    val tokenHash = char("token_hash", 64)
    val accountId = uuid("account_id").index("idx_refresh_tokens_account")
    val familyId = uuid("family_id").index("idx_refresh_tokens_family")
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumedAt = timestampWithTimeZone("consumed_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(tokenHash)
}

/** `V5__create_brands.sql` — brand/location split (sc-155). */
object Brands : Table("brands") {
    val id = uuid("id")
    val name = varchar("name", 255).uniqueIndex("uq_brands_name")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * `V4` → `V20`: the write-path listing table.
 *
 * Nullability mirrors the migrations: `owner_id` and `cuisine` became nullable in
 * V6 (community/research seeds), the structured address fields are nullable
 * (V20, legacy rows), `is_hand_cut` (V17) and `is_delivery` (V19) are tri-state
 * booleans (`NULL` = unknown / not claimed), `price` (V9) and `rating` (V10) are
 * optional, and `alcohol_served` / `halal_scope` / `cross_contamination` carry
 * defaults from V11/V18.
 *
 * FKs (SQL-side): `owner_id -> users(id)`, `brand_id -> brands(id)`.
 *
 * NOT mapped: `location` (PostGIS geography) and the generated
 * `location_lat_6` / `location_lng_6` columns (V6).
 */
object RestaurantListings : Table("restaurant_listings") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val address = varchar("address", 512)
    val city = varchar("city", 128).nullable()
    val province = varchar("province", 64).nullable()
    val postal = varchar("postal", 32).nullable()
    val country = varchar("country", 16).nullable()
    val cuisine = varchar("cuisine", 64).nullable()
    val isHandCut = bool("is_hand_cut").nullable()
    val isDelivery = bool("is_delivery").nullable()
    val ownerId = uuid("owner_id").index("idx_restaurant_listings_owner").nullable()
    val brandId = uuid("brand_id").nullable()
    val provenance = varchar("provenance", 64).nullable()
    val venueKey = char("venue_key", 64).nullable()
    val verificationStatus = varchar("verification_status", 32)
    val createdAt = timestampWithTimeZone("created_at")
    val price = decimal("price", 10, 2).nullable()
    val rating = decimal("rating", 3, 2).nullable()
    val alcoholServed = bool("alcohol_served")
    val halalScope = varchar("halal_scope", 32)
    val crossContamination = varchar("cross_contamination", 32)

    /** SC-205: opaque hero-photo version (0 = no photo); V25. */
    val imageVersion = long("image_version")

    override val primaryKey = PrimaryKey(id)
}

/**
 * `V8__create_listing_search.sql` (extended by V9/V10/V11/V17/V18/V19/V20): the
 * denormalised read model the search surface reads alone.
 *
 * 1:1 with [RestaurantListings] (`id` FK, `ON DELETE CASCADE`) and only ever
 * populated for cross-contamination index-qualified rows. `location` is
 * PostGIS-only and therefore not mapped here either.
 */
object ListingSearch : Table("listing_search") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val address = varchar("address", 512)
    val city = varchar("city", 128).nullable()
    val province = varchar("province", 64).nullable()
    val postal = varchar("postal", 32).nullable()
    val country = varchar("country", 16).nullable()
    val cuisine = varchar("cuisine", 64).nullable()
    val isHandCut = bool("is_hand_cut").nullable()
    val isDelivery = bool("is_delivery").nullable()
    val verificationStatus = varchar("verification_status", 32)
    val price = decimal("price", 10, 2).nullable()
    val rating = decimal("rating", 3, 2).nullable()
    val alcoholServed = bool("alcohol_served")
    val halalScope = varchar("halal_scope", 32)
    val crossContamination = varchar("cross_contamination", 32)

    /** SC-205: mirrors `restaurant_listings.image_version` so search cards version their URLs too. */
    val imageVersion = long("image_version")

    override val primaryKey = PrimaryKey(id)
}

/**
 * `V9__sc_43_44_price_cuisine_filters.sql` — authoritative multi-cuisine store
 * (one row per listing/cuisine pair). FK: `listing_id -> restaurant_listings(id)
 * ON DELETE CASCADE`.
 */
object RestaurantListingCuisines : Table("restaurant_listing_cuisines") {
    val listingId = uuid("listing_id")
    val cuisine = varchar("cuisine", 64).index("idx_restaurant_listing_cuisines_cuisine")

    override val primaryKey = PrimaryKey(listingId, cuisine)
}

/**
 * `V18__sc_119_partial_halal.sql` — per-item halal scope child rows.
 * FK: `listing_id -> restaurant_listings(id) ON DELETE CASCADE`.
 */
object RestaurantHalalItems : Table("restaurant_halal_items") {
    val listingId = uuid("listing_id")
    val name = varchar("name", 128)
    val isHalal = bool("is_halal")

    override val primaryKey = PrimaryKey(listingId, name)
}

/**
 * `V12__create_favorites.sql` — user ↔ listing favourite relation. The composite
 * primary key is what makes the POST contract idempotent at the DB level.
 * FKs: both columns `ON DELETE CASCADE`.
 */
object Favorites : Table("favorites") {
    val userId = uuid("user_id")
    val listingId = uuid("listing_id")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId, listingId)
}

/**
 * `V13__create_halal_certification_reviews.sql` + V14 (AI consent) + V16
 * (certifier / expiry) + V21 (certificate key). One row per owner claim; the
 * whole review aggregate (state, AI suggestion, human decision, consent,
 * certificate metadata and the identity of the reviewed evidence) lives in this
 * row, so every read yields a consistent snapshot.
 *
 * FKs (SQL-side, `NO ACTION`): `listing_id -> restaurant_listings(id)`,
 * `submitted_by -> users(id)`.
 */
object HalalCertificationReviews : Table("halal_certification_reviews") {
    val id = uuid("id")
    val listingId = uuid("listing_id").index("idx_halal_cert_review_listing")
    val submittedBy = uuid("submitted_by")
    val state = varchar("state", 32).index("idx_halal_cert_review_state")
    val suggestionVerdict = varchar("suggestion_verdict", 32).nullable()
    val suggestionConfidence = double("suggestion_confidence").nullable()
    val suggestionReasoning = text("suggestion_reasoning").nullable()
    val decisionOutcome = varchar("decision_outcome", 32).nullable()
    val decisionBy = uuid("decision_by").nullable()
    val decisionReason = text("decision_reason").nullable()
    val decisionAt = timestampWithTimeZone("decision_at").nullable()
    val aiConsentAt = timestampWithTimeZone("ai_consent_at").nullable()
    val certifier = varchar("certifier", 200).nullable()
    val expiresOn = date("expires_on").nullable()
    val certificateKey = varchar("certificate_key", 300).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * `V23__sc_198_geocoded_addresses.sql` (SC-198): the geocode cache.
 *
 * One row per normalized query; the SHA-256 hash is the primary key, so a write
 * is an idempotent upsert and a read is a primary-key lookup. `created_at` is
 * both first-sighting and freshness: rows older than the TTL are not served and
 * are refreshed in place (docs/features/SC-198, D-6).
 */
object GeocodedAddresses : Table("geocoded_addresses") {
    val normalizedQueryHash = varchar("normalized_query_hash", 64)
    val lat = double("lat")
    val lng = double("lng")
    val displayName = text("display_name")
    val providerRef = text("provider_ref").nullable()
    val provider = varchar("provider", 32)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(normalizedQueryHash)
}

/**
 * `V22__sc_211_ingestion_schema.sql` — cited certifier observations.
 *
 * Unique by `(source, source_record_ref)` globally; the listing FK is the audit
 * link. `hand_cut_declaration` stores the certifier's three-state policy so
 * "declared not" stays distinguishable from "silent".
 */
object ExternalCertificationClaims : Table("external_certification_claims") {
    val id = uuid("id")
    val listingId = uuid("listing_id").index("idx_external_claims_listing")
    val claimSource = varchar("source", 64)
    val sourceTier = short("source_tier")
    val sourceRecordRef = varchar("source_record_ref", 255)
    val sourceUrl = varchar("source_url", 1024)
    val certifier = varchar("certifier", 255)
    val expiresOn = date("expires_on").nullable()
    val handCutDeclaration = varchar("hand_cut_declaration", 32)
    val firstSeen = timestampWithTimeZone("first_seen").nullable()
    val lastSeen = timestampWithTimeZone("last_seen").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
