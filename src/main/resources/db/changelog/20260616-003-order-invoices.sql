-- liquibase formatted sql

-- changeset mehdi:20260616-003-order-invoices
CREATE TABLE blitzpay.order_invoices (
    id               UUID          PRIMARY KEY,
    order_id_fk      UUID          NOT NULL,
    order_id         VARCHAR(64)   NOT NULL,
    s3_key           VARCHAR(512)  NOT NULL,
    generated_at     TIMESTAMPTZ   NOT NULL,
    delivery_status  VARCHAR(16)   NOT NULL,
    CONSTRAINT fk_order_invoices_order FOREIGN KEY (order_id_fk)
        REFERENCES blitzpay.order_orders (id)
);

CREATE UNIQUE INDEX uq_order_invoices_order_id
    ON blitzpay.order_invoices (order_id);

CREATE INDEX ix_order_invoices_delivery_status
    ON blitzpay.order_invoices (delivery_status);

-- rollback DROP TABLE blitzpay.order_invoices;
