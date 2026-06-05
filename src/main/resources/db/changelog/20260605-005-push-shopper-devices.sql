-- liquibase formatted sql

-- changeset mehdi:20260605-005-push-shopper-devices
CREATE TABLE blitzpay.push_shopper_device_registrations (
    id                 UUID         NOT NULL PRIMARY KEY,
    shopper_account_id VARCHAR(256) NOT NULL,
    expo_push_token    VARCHAR(256) NOT NULL,
    platform           VARCHAR(16)  NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_seen_at       TIMESTAMPTZ  NOT NULL,
    invalid            BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (expo_push_token)
);

CREATE INDEX idx_push_shopper_device_shopper ON blitzpay.push_shopper_device_registrations(shopper_account_id) WHERE invalid = FALSE;

-- rollback DROP TABLE blitzpay.push_shopper_device_registrations;
