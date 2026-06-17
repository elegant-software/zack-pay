-- liquibase formatted sql

-- changeset mehdi:20260617-002-widen-buyer-contact-country
ALTER TABLE blitzpay.order_orders
    ALTER COLUMN buyer_contact_country TYPE VARCHAR(64);

-- rollback ALTER TABLE blitzpay.order_orders ALTER COLUMN buyer_contact_country TYPE CHAR(2) USING buyer_contact_country::CHAR(2);
