-- liquibase formatted sql

-- changeset mehdi:20260616-001-order-buyer-contact
ALTER TABLE blitzpay.order_orders
    ADD COLUMN buyer_contact_name    VARCHAR(255) NULL,
    ADD COLUMN buyer_contact_street  VARCHAR(255) NULL,
    ADD COLUMN buyer_contact_zip     VARCHAR(32)  NULL,
    ADD COLUMN buyer_contact_city    VARCHAR(128) NULL,
    ADD COLUMN buyer_contact_country CHAR(2)      NULL;

-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN buyer_contact_name, DROP COLUMN buyer_contact_street, DROP COLUMN buyer_contact_zip, DROP COLUMN buyer_contact_city, DROP COLUMN buyer_contact_country;
