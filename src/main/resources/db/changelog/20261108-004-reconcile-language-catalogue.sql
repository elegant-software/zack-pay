-- liquibase formatted sql

-- changeset hamid:20261108-004-reconcile-language-catalogue splitStatements:false
-- Earlier local runs may already own some/all columns. Complete the schema without dropping data.
ALTER TABLE blitzpay.merchant_menu_languages
    ADD COLUMN IF NOT EXISTS flag_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS direction VARCHAR(3) NOT NULL DEFAULT 'ltr',
    ADD COLUMN IF NOT EXISTS admin_ui_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS menu_enabled BOOLEAN NOT NULL DEFAULT TRUE;

DO $language_catalogue$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'blitzpay.merchant_menu_languages'::regclass
          AND conname = 'chk_merchant_menu_languages_direction'
    ) THEN
        ALTER TABLE blitzpay.merchant_menu_languages
            ADD CONSTRAINT chk_merchant_menu_languages_direction CHECK (direction IN ('ltr', 'rtl'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'blitzpay.merchant_menu_languages'::regclass
          AND conname = 'chk_merchant_menu_languages_flag'
    ) THEN
        ALTER TABLE blitzpay.merchant_menu_languages
            ADD CONSTRAINT chk_merchant_menu_languages_flag CHECK (flag_code IS NULL OR flag_code ~ '^[A-Z]{2}$');
    END IF;
END
$language_catalogue$;

UPDATE blitzpay.merchant_menu_languages
SET direction = 'rtl'
WHERE code IN ('ar', 'fa', 'he', 'ur', 'yi', 'ps', 'dv', 'sd', 'ug');

UPDATE blitzpay.merchant_menu_languages
SET admin_ui_enabled = TRUE,
    flag_code = CASE code WHEN 'en' THEN 'GB' WHEN 'de' THEN 'DE' WHEN 'ar' THEN 'SA' END
WHERE code IN ('en', 'de', 'ar');

-- rollback SELECT 1; -- Reconciliation cannot safely distinguish pre-existing columns/data from newly created metadata.
