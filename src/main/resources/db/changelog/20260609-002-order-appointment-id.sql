-- liquibase formatted sql

-- changeset mehdi:20260609-002-order-appointment-id
ALTER TABLE blitzpay.order_orders
    ADD COLUMN appointment_id UUID NULL;

CREATE UNIQUE INDEX uq_order_appointment_id
    ON blitzpay.order_orders (appointment_id)
    WHERE appointment_id IS NOT NULL;

-- rollback DROP INDEX IF EXISTS blitzpay.uq_order_appointment_id; ALTER TABLE blitzpay.order_orders DROP COLUMN appointment_id;
