-- liquibase formatted sql

-- changeset codex:20261105-002-add-station-ticket-snapshots
ALTER TABLE blitzpay.order_items
    ADD COLUMN preparation_station_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    ADD COLUMN preparation_station_name VARCHAR(100) NOT NULL DEFAULT 'Unassigned',
    ADD COLUMN preparation_status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED';
UPDATE blitzpay.order_items i SET preparation_status = CASE WHEN i.locked THEN 'READY' ELSE o.preparation_status END
FROM blitzpay.order_orders o WHERE o.id = i.order_id_fk;
-- rollback ALTER TABLE blitzpay.order_items DROP COLUMN preparation_status, DROP COLUMN preparation_station_name, DROP COLUMN preparation_station_id;
