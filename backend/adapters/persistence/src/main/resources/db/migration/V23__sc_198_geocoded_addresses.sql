-- SC-198: geocode cache (cost hedge) — one row per normalized place query.
--
-- Problem: PhotonGeocoder calls photon.komoot.io on every place lookup. No SLA,
-- IP rate-limits under burst, and a future paid provider costs ~$5/1k requests.
--
-- This table is the cache behind GeocoderPort: the normalized query hash is the
-- identity, so the same street/venue/neighbourhood costs one external call ever.
--
-- Semantics (docs/features/SC-198):
--  * `normalized_query_hash` = lowercase hex SHA-256 of the normalized query
--    (NFKC, trimmed, whitespace collapsed, lowercased). Primary key = the
--    idempotency: writes are INSERT … ON CONFLICT (hash) DO UPDATE.
--  * `created_at` is also the freshness stamp: a row older than 90 days is not
--    served and is refreshed in place (D-6). No scheduler; reads filter on age.
--  * `provider` records which service produced the row, so a provider swap can
--    purge cleanly; `provider_ref` keeps GeocodedPlace.providerRef intact.
--  * The raw query text is deliberately NOT stored (data minimization).
CREATE TABLE geocoded_addresses (
    normalized_query_hash CHAR(64)        PRIMARY KEY,
    lat                   DOUBLE PRECISION NOT NULL,
    lng                   DOUBLE PRECISION NOT NULL,
    display_name          TEXT             NOT NULL,
    provider_ref          TEXT,
    provider              VARCHAR(32)      NOT NULL,
    created_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_geocoded_addresses_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_geocoded_addresses_lng CHECK (lng BETWEEN -180 AND 180)
);
