-- liquibase formatted sql

-- changeset mehdi:20260605-004-appointments-table
CREATE TABLE blitzpay.appointments (
    id                       UUID           NOT NULL PRIMARY KEY,
    merchant_id              UUID           NOT NULL,
    branch_id                UUID           NOT NULL,
    employee_id              UUID           NOT NULL,
    product_id               UUID           NOT NULL,
    shopper_account_id       VARCHAR(256)   NULL,
    start_time               TIMESTAMPTZ    NOT NULL,
    end_time                 TIMESTAMPTZ    NOT NULL,
    status                   VARCHAR(32)    NOT NULL DEFAULT 'BOOKED',
    contact_type             VARCHAR(32)    NOT NULL,
    contact_name             VARCHAR(255)   NOT NULL,
    contact_email            VARCHAR(255)   NULL,
    contact_phone            VARCHAR(64)    NULL,
    product_snapshot_name    VARCHAR(255)   NOT NULL,
    product_snapshot_price   DECIMAL(12, 4) NULL,
    duration_snapshot_minutes INT           NOT NULL,
    note                     TEXT           NULL,
    override_reason          TEXT           NULL,
    reminder_due_at          TIMESTAMPTZ    NULL,
    reminder_sent            BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_appointments_merchant       ON blitzpay.appointments(merchant_id, start_time);
CREATE INDEX idx_appointments_branch         ON blitzpay.appointments(branch_id, start_time);
CREATE INDEX idx_appointments_employee       ON blitzpay.appointments(employee_id, start_time, end_time) WHERE status = 'BOOKED';
CREATE INDEX idx_appointments_shopper        ON blitzpay.appointments(shopper_account_id, start_time) WHERE shopper_account_id IS NOT NULL;
CREATE INDEX idx_appointments_reminder       ON blitzpay.appointments(reminder_due_at) WHERE reminder_sent = FALSE AND shopper_account_id IS NOT NULL AND status = 'BOOKED';

CREATE UNIQUE INDEX ux_appointments_employee_start ON blitzpay.appointments(employee_id, start_time) WHERE status = 'BOOKED';

-- rollback DROP TABLE blitzpay.appointments;
