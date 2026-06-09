-- liquibase formatted sql

-- changeset mehdi:20260609-001-appointment-employee-name-category
ALTER TABLE blitzpay.appointments
    ADD COLUMN employee_snapshot_name VARCHAR(255) NULL,
    ADD COLUMN product_category_id    UUID         NULL;

-- rollback ALTER TABLE blitzpay.appointments DROP COLUMN employee_snapshot_name, DROP COLUMN product_category_id;
