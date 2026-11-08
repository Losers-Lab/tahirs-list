-- SC-211 ingestion schema: cited certifier claims + deterministic venue identity.
--
-- The runtime load (D-15) writes through RestaurantListingRepository, so this
-- migration is schema only; there is no generated data migration (V23 retired).
--
-- Identity (D-18):
--   * external_certification_claims is unique by (source, source_record_ref)
--     globally. The same source record must never attach to two listings.
--   * restaurant_listings.venue_key is a SHA-256 of normalized name + address +
--     city/region/country. It is unique where present; coordinates are excluded
--     because Photon can resolve the same venue differently across runs.
--   * location remains a report-only fallback in the load, never the identity.

-- 1. Widen the provenance vocabulary for the three certifiers + the manual batch.
ALTER TABLE restaurant_listings DROP CONSTRAINT ck_restaurant_listings_provenance;
ALTER TABLE restaurant_listings ADD CONSTRAINT ck_restaurant_listings_provenance CHECK (
    provenance IS NULL OR provenance IN (
        'research-seed',
        'photon-geocode',
        'research-seed / photon-geocode',
        'certifier-hms',
        'certifier-hfsaa',
        'certifier-hma',
        'manual-zabihah'
    )
);

-- 2. Deterministic venue identity.
ALTER TABLE restaurant_listings ADD COLUMN venue_key CHAR(64);
ALTER TABLE restaurant_listings ADD CONSTRAINT ck_restaurant_listings_venue_key CHECK (
    venue_key IS NULL OR venue_key ~ '^[0-9a-f]{64}$'
);
CREATE UNIQUE INDEX uq_restaurant_listings_venue_key
    ON restaurant_listings (venue_key)
    WHERE venue_key IS NOT NULL;

-- 2b. One ingested venue per coordinate pair (D-18). Seed rows and ingestion
--     rows share the same space, so a row that matches an existing row only by
--     location is rejected here too; the load holds it as SUSPECTED_DUPLICATE
--     before ever reaching this backstop.
--
--     This index subsumes V6's narrower seed-location index, so the seed index
--     is dropped. PostgreSQL still infers this index for V7's
--     `ON CONFLICT (location_lat_6, location_lng_6) WHERE provenance =
--     'research-seed / photon-geocode'` because that predicate implies
--     `provenance IS NOT NULL`, so re-running the seed corpus stays a no-op.
DROP INDEX uq_restaurant_listings_seed_location;
CREATE UNIQUE INDEX uq_restaurant_listings_research_location
    ON restaurant_listings (location_lat_6, location_lng_6)
    WHERE provenance IS NOT NULL;

-- 3. Cited certifier observations. The listing FK is the audit link and is
--    RESTRICT, so deleting a listing can never silently destroy a claim or free
--    the global (source, source_record_ref) idempotency key. The globally unique
--    source record is the idempotency key.
CREATE TABLE external_certification_claims (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    listing_id          UUID        NOT NULL REFERENCES restaurant_listings (id) ON DELETE RESTRICT,
    source              VARCHAR(64) NOT NULL,
    source_tier         SMALLINT    NOT NULL,
    source_record_ref   VARCHAR(255) NOT NULL,
    source_url          VARCHAR(1024) NOT NULL,
    certifier           VARCHAR(255) NOT NULL,
    expires_on          DATE,
    hand_cut_declaration VARCHAR(32) NOT NULL,
    first_seen          TIMESTAMPTZ,
    last_seen           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_external_certification_claims PRIMARY KEY (id),
    CONSTRAINT ck_external_claims_source CHECK (
        source = lower(source) AND source ~ '^[a-z0-9-]+$'
    ),
    CONSTRAINT ck_external_claims_tier CHECK (source_tier BETWEEN 1 AND 3),
    CONSTRAINT ck_external_claims_url CHECK (source_url ~ '^https://[^[:space:]]+$'),
    CONSTRAINT ck_external_claims_hand_cut CHECK (
        hand_cut_declaration IN ('HAND_CUT', 'NOT_HAND_CUT', 'UNSPECIFIED')
    ),
    CONSTRAINT ck_external_claims_seen CHECK (
        first_seen IS NULL OR last_seen IS NULL OR last_seen >= first_seen
    )
);

CREATE UNIQUE INDEX uq_external_claims_source_record
    ON external_certification_claims (source, source_record_ref);
CREATE INDEX idx_external_claims_listing
    ON external_certification_claims (listing_id);
