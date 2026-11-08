-- liquibase formatted sql

-- changeset mehdi:20261108-001-merchant-floor-plan-areas
-- Floor Areas traced once over the Floor Plan picture: named rectangles ("Bar", "Terrace") as x/y/width/height
-- fractions (0..1) of the picture, the same frame as Table Placements. The live floor draws them instead of the picture.
ALTER TABLE blitzpay.merchant_floor_plans ADD COLUMN areas JSONB NOT NULL DEFAULT '[]'::jsonb;
-- rollback ALTER TABLE blitzpay.merchant_floor_plans DROP COLUMN areas;
