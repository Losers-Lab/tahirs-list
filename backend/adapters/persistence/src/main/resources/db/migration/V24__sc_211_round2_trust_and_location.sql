-- SC-211 review round 2: make certifier-derived trust explicit and stop
-- forbidding two legitimate listings at one point.
--
-- 1. verification_basis records WHERE the current trust came from. The
--    ingestion load may only recompute (and therefore only demote) trust it
--    derived from a Tier-1 claim; a committee-verified row (basis NULL) is
--    never touched by an ingestion batch (D-10).
ALTER TABLE restaurant_listings ADD COLUMN verification_basis VARCHAR(32);
ALTER TABLE restaurant_listings ADD CONSTRAINT ck_restaurant_listings_basis CHECK (
    verification_basis IS NULL OR verification_basis = 'CERTIFIER_REGISTRY'
);

-- Existing certifier-verified rows (none in the seed corpus, which is
-- UNVERIFIED, but be safe) are marked so a refresh can recompute them.
UPDATE restaurant_listings l
SET verification_basis = 'CERTIFIER_REGISTRY'
WHERE l.verification_status = 'VERIFIED'
  AND EXISTS (
      SELECT 1 FROM external_certification_claims c
      WHERE c.listing_id = l.id AND c.source_tier = 1
  );

-- 2. Two different restaurants can legitimately share one point (a food court,
--    a mall suite). Identity is the claim / venue key, not the coordinate, so
--    the broad unique index becomes a plain lookup index. The seed corpus keeps
--    its own uniqueness, because V7's `ON CONFLICT` depends on it and re-running
--    the seed must stay a no-op.
DROP INDEX uq_restaurant_listings_research_location;
CREATE INDEX idx_restaurant_listings_research_location
    ON restaurant_listings (location_lat_6, location_lng_6)
    WHERE provenance IS NOT NULL;
CREATE UNIQUE INDEX uq_restaurant_listings_seed_location
    ON restaurant_listings (location_lat_6, location_lng_6)
    WHERE provenance = 'research-seed / photon-geocode';
