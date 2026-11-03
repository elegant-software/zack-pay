-- liquibase formatted sql

-- changeset mehdi:20261103-002-order-preparation-status
-- Kitchen-side lifecycle (RECEIVED -> PREPARING -> READY), independent of the payment status column.
-- Existing rows get RECEIVED through the default.
ALTER TABLE blitzpay.order_orders
    ADD COLUMN preparation_status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    ADD COLUMN pickup_number      INTEGER;
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN pickup_number, DROP COLUMN preparation_status;

-- changeset mehdi:20261103-002-order-items-locked
-- An item is locked once the Order it belongs to reached READY and a new item was added afterwards (already made).
ALTER TABLE blitzpay.order_items
    ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
-- rollback ALTER TABLE blitzpay.order_items DROP COLUMN locked;

-- changeset mehdi:20261103-002-order-pickup-counters
-- Last Pickup Number handed out per Branch and Branch-local business day. Rows are upserted (row-locked)
-- inside the order-creation transaction, so concurrent orders of the same Branch get distinct numbers.
CREATE TABLE blitzpay.order_pickup_counters (
    branch_id    UUID    NOT NULL,
    business_day DATE    NOT NULL,
    last_number  INTEGER NOT NULL,
    CONSTRAINT pk_order_pickup_counters PRIMARY KEY (branch_id, business_day)
);
-- rollback DROP TABLE blitzpay.order_pickup_counters;

-- changeset mehdi:20261103-002-order-orders-branch-created-at-index
-- Kitchen Screen reads a Branch's Orders of the current day.
CREATE INDEX ix_order_orders_merchant_branch_id_created_at
    ON blitzpay.order_orders (merchant_branch_id, created_at);
-- rollback DROP INDEX IF EXISTS blitzpay.ix_order_orders_merchant_branch_id_created_at;
