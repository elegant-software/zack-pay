-- liquibase formatted sql

-- changeset codex:20260529-001-order-checkout-payment-method
ALTER TABLE blitzpay.order_orders
    ADD COLUMN checkout_payment_method VARCHAR(32) NULL;

-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN checkout_payment_method;
