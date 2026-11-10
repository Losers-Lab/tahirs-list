-- SC-231: enrichment columns and the cover-photo table.
--
-- The catalogue holds restaurants only (L-1), so `venue_type` describes *which
-- kind of restaurant*, not whether the row is one. The ingestion gate decides
-- that before the load, which is why the column is nullable: seed and owner rows
-- predate the gate, an enriched ingested row always carries a type.
--
-- The cover-photo table is where a licence becomes a constraint rather than a
-- comment: a Google Places row can only be a CACHE with an expiry, and a
-- free-licensed row can only exist with the licence that permits its reuse.

ALTER TABLE restaurant_listings
    ADD COLUMN venue_type VARCHAR(16),
    ADD COLUMN venue_type_source VARCHAR(24),
    ADD COLUMN cuisine_source VARCHAR(24);

ALTER TABLE restaurant_listings
    ADD CONSTRAINT ck_restaurant_listings_venue_type
        CHECK (venue_type IS NULL OR venue_type IN ('RESTAURANT', 'CAFE', 'FAST_FOOD', 'BAKERY')),
    ADD CONSTRAINT ck_restaurant_listings_venue_type_source
        CHECK (venue_type_source IS NULL OR venue_type_source IN
            ('SOURCE_CATEGORY', 'OSM_FEATURE', 'BRAND_MAP', 'NAME_KEYWORD')),
    ADD CONSTRAINT ck_restaurant_listings_cuisine_source
        CHECK (cuisine_source IS NULL OR cuisine_source IN
            ('SOURCE_CATEGORY', 'OSM_FEATURE', 'BRAND_MAP', 'NAME_KEYWORD')),
    -- A derived value without the evidence that produced it is not auditable.
    ADD CONSTRAINT ck_restaurant_listings_venue_type_paired
        CHECK ((venue_type IS NULL) = (venue_type_source IS NULL));

CREATE INDEX idx_restaurant_listings_venue_type
    ON restaurant_listings (venue_type)
    WHERE venue_type IS NOT NULL;

CREATE TABLE listing_cover_photos (
    listing_id        UUID PRIMARY KEY REFERENCES restaurant_listings (id) ON DELETE CASCADE,
    source_kind       VARCHAR(16) NOT NULL,
    storage_class     VARCHAR(16) NOT NULL,
    licence           VARCHAR(16),
    attribution       VARCHAR(1024) NOT NULL,
    author            VARCHAR(512),
    source_url        VARCHAR(1024),
    google_place_id   VARCHAR(255),
    google_photo_name VARCHAR(1024),
    expires_at        TIMESTAMPTZ,
    content_hash      CHAR(64),
    width             INT,
    height            INT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_listing_cover_photos_source
        CHECK (source_kind IN ('OWNER_UPLOAD', 'OSM', 'WIKIMEDIA', 'GOOGLE_PLACES')),
    CONSTRAINT ck_listing_cover_photos_storage
        CHECK (storage_class IN ('PERMANENT', 'CACHE')),
    CONSTRAINT ck_listing_cover_photos_licence
        CHECK (licence IS NULL OR licence IN ('CC0', 'PUBLIC_DOMAIN', 'CC_BY', 'CC_BY_SA')),
    -- A cache without an expiry could be served forever, which is exactly what
    -- the Places terms forbid.
    CONSTRAINT ck_listing_cover_photos_cache_expiry
        CHECK (storage_class = 'PERMANENT' OR expires_at IS NOT NULL),
    -- The licence, expressed where it cannot be forgotten.
    CONSTRAINT ck_listing_cover_photos_places_is_cache
        CHECK (source_kind <> 'GOOGLE_PLACES' OR (storage_class = 'CACHE' AND expires_at IS NOT NULL)),
    CONSTRAINT ck_listing_cover_photos_free_has_licence
        CHECK (source_kind NOT IN ('OSM', 'WIKIMEDIA') OR licence IS NOT NULL),
    CONSTRAINT ck_listing_cover_photos_owner_has_no_licence
        CHECK (source_kind <> 'OWNER_UPLOAD' OR licence IS NULL)
);

COMMENT ON TABLE listing_cover_photos IS
    'One cover photo per listing. PERMANENT rows are ours to keep; CACHE rows (Google Places) must expire and are never served after expires_at.';
