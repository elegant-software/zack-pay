-- liquibase formatted sql

-- changeset mehdi:20261108-002-orders-guest-invoice-email
-- A storefront guest may give an email to receive the invoice, only with explicit consent (DSGVO Art. 6(1)(a)).
-- invoice_email is cleared once the invoice is sent; consent time and wording version stay as proof of consent.
ALTER TABLE blitzpay.orders
    ADD COLUMN invoice_email VARCHAR(254),
    ADD COLUMN invoice_email_consent_at TIMESTAMPTZ,
    ADD COLUMN invoice_email_consent_version VARCHAR(16),
    ADD CONSTRAINT chk_orders_invoice_email_consent CHECK (invoice_email IS NULL OR invoice_email_consent_at IS NOT NULL);
-- rollback ALTER TABLE blitzpay.orders DROP CONSTRAINT chk_orders_invoice_email_consent, DROP COLUMN invoice_email_consent_version, DROP COLUMN invoice_email_consent_at, DROP COLUMN invoice_email;
