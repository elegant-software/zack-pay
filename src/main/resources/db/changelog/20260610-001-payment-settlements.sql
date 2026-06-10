-- liquibase formatted sql

-- changeset mehdi:20260610-001-payment-settlements
CREATE TABLE blitzpay.payment_settlements (
    id                          UUID         PRIMARY KEY,
    payment_request_id          UUID         NOT NULL,
    order_id_fk                 UUID         NOT NULL,
    order_id                    VARCHAR(64)  NOT NULL,
    provider                    VARCHAR(32)  NOT NULL,
    currency                    CHAR(3)      NOT NULL,
    gross_amount_minor          BIGINT       NOT NULL,
    processing_fee_minor        BIGINT       NOT NULL,
    platform_fee_minor          BIGINT       NOT NULL,
    net_amount_minor            BIGINT       NOT NULL,
    payment_method              VARCHAR(32)  NOT NULL,
    payment_method_detail       VARCHAR(64)  NULL,
    provider_charge_reference   VARCHAR(255) NULL,
    provider_balance_tx_reference VARCHAR(255) NULL,
    settled_at                  TIMESTAMPTZ  NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_payment_settlements_payment_request_id
    ON blitzpay.payment_settlements (payment_request_id);

CREATE INDEX ix_payment_settlements_order_id_fk
    ON blitzpay.payment_settlements (order_id_fk);

CREATE INDEX ix_payment_settlements_settled_at
    ON blitzpay.payment_settlements (settled_at);

-- rollback DROP TABLE blitzpay.payment_settlements;
