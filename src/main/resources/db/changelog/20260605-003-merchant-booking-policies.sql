-- liquibase formatted sql

-- changeset mehdi:20260605-003-merchant-booking-policies
ALTER TABLE blitzpay.merchant_applications
    ADD COLUMN booking_interval_minutes   INT NULL,
    ADD COLUMN booking_lead_time_minutes  INT NULL,
    ADD COLUMN booking_horizon_days       INT NULL,
    ADD COLUMN reminder_lead_time_minutes INT NULL;

-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN booking_interval_minutes, DROP COLUMN booking_lead_time_minutes, DROP COLUMN booking_horizon_days, DROP COLUMN reminder_lead_time_minutes;
