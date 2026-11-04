-- liquibase formatted sql

-- changeset mehdi:20261104-002-order-orders-table
-- The Table a Walk-In order was placed at, with a snapshot of its Table Name at order time.
-- RESTRICT: a Table with orders can only be deactivated, never deleted.
ALTER TABLE blitzpay.order_orders
    ADD COLUMN table_id   UUID,
    ADD COLUMN table_name VARCHAR(100),
    ADD CONSTRAINT fk_order_orders_merchant_tables
        FOREIGN KEY (table_id) REFERENCES blitzpay.merchant_tables (id) ON DELETE RESTRICT;
CREATE INDEX ix_order_orders_table_id_created_at ON blitzpay.order_orders (table_id, created_at);
-- rollback DROP INDEX IF EXISTS blitzpay.ix_order_orders_table_id_created_at;
-- rollback ALTER TABLE blitzpay.order_orders DROP CONSTRAINT fk_order_orders_merchant_tables, DROP COLUMN table_name, DROP COLUMN table_id;
