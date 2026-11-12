-- SC-231 round 2: the constraints the Kotlin model already enforces.
--
-- V27 left the database looser than `CoverPhoto`/`RestaurantListing`, and a row
-- that only the database allows is a row that makes a read throw (the mapper
-- constructs the domain object). These CHECKs close the gap so the two agree.

ALTER TABLE listing_cover_photos
    -- A free-licensed photo is ours to keep; a cache would throw it away.
    ADD CONSTRAINT ck_listing_cover_photos_free_is_permanent
        CHECK (source_kind NOT IN ('OSM', 'WIKIMEDIA') OR storage_class = 'PERMANENT'),
    -- Without the place id a Places cache cannot be refreshed.
    ADD CONSTRAINT ck_listing_cover_photos_places_has_place_id
        CHECK (source_kind <> 'GOOGLE_PLACES' OR google_place_id IS NOT NULL),
    -- Credit is the licence condition, not decoration.
    ADD CONSTRAINT ck_listing_cover_photos_attribution_not_blank
        CHECK (btrim(attribution) <> ''),
    -- CC-BY/CC-BY-SA require the author to be named.
    ADD CONSTRAINT ck_listing_cover_photos_attribution_credit
        CHECK (
            licence IS NULL
            OR licence NOT IN ('CC_BY', 'CC_BY_SA')
            OR (author IS NOT NULL AND btrim(author) <> '')
        ),
    -- The credit link is rendered as an anchor; only https is safe there.
    ADD CONSTRAINT ck_listing_cover_photos_source_url_https
        CHECK (source_url IS NULL OR source_url LIKE 'https://%');

ALTER TABLE restaurant_listings
    -- A derived cuisine without the cuisine is not auditable.
    ADD CONSTRAINT ck_restaurant_listings_cuisine_source_needs_cuisine
        CHECK (cuisine_source IS NULL OR cuisine IS NOT NULL);
