-- SC-205: give the listing's hero photo an opaque version.
--
-- The public image endpoint is served with `public, max-age=86400` and its URL
-- used to carry no version, so a replaced photo could be invisible for a day.
-- The read surface now appends `&v=<image_version>` to every image URL; the
-- image route ignores the parameter, and it only varies the cache key.
--
-- 0 means "no photo" and keeps the URL clean for rows nothing was ever stored
-- for. Existing rows may already have a stored photo (seed ingest), so they are
-- stamped once: their next request re-fetches under the versioned URL, and any
-- later upload writes a strictly larger epoch-millis value.
ALTER TABLE restaurant_listings ADD COLUMN image_version BIGINT NOT NULL DEFAULT 0;

-- The search read model is a separate table, so it carries the same stamp;
-- otherwise a replaced photo would stay stale on search cards for a day.
ALTER TABLE listing_search ADD COLUMN image_version BIGINT NOT NULL DEFAULT 0;

UPDATE restaurant_listings SET image_version = 1;
UPDATE listing_search SET image_version = 1;
