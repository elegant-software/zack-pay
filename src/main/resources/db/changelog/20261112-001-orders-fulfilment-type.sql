-- liquibase formatted sql

-- changeset mehdi:20261112-001-orders-fulfilment-type
-- Fulfilment Type (ADR-0025): how a Walk-In or Pre-Order is consumed. NULL for Invoice Orders and Appointment Orders.
ALTER TABLE blitzpay.order_orders
    ADD COLUMN fulfilment_type VARCHAR(16),
    ADD CONSTRAINT chk_order_orders_fulfilment_type CHECK (fulfilment_type IN ('DINE_IN', 'TAKE_AWAY'));

UPDATE blitzpay.order_orders
SET fulfilment_type = CASE WHEN table_id IS NOT NULL THEN 'DINE_IN' ELSE 'TAKE_AWAY' END
WHERE target_shopper_id IS NULL AND appointment_id IS NULL;
-- rollback ALTER TABLE blitzpay.order_orders DROP CONSTRAINT chk_order_orders_fulfilment_type, DROP COLUMN fulfilment_type;
