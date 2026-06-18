--liquibase formatted sql

--changeset invoice-order:add-target-shopper-and-due-date
ALTER TABLE blitzpay.order_orders
    ADD COLUMN target_shopper_id VARCHAR(512),
    ADD COLUMN due_date DATE;

CREATE INDEX idx_order_orders_target_shopper_id
    ON blitzpay.order_orders (target_shopper_id)
    WHERE target_shopper_id IS NOT NULL;

--changeset invoice-order:add-shopper-profile-email
ALTER TABLE blitzpay.shopper_profiles
    ADD COLUMN email VARCHAR(255);

CREATE UNIQUE INDEX idx_shopper_profiles_email
    ON blitzpay.shopper_profiles (email)
    WHERE email IS NOT NULL;
