-- liquibase formatted sql

-- changeset hamid:20261108-003-shared-language-catalogue
-- Extend the existing catalogue; merchant language assignments and translations stay intact.
ALTER TABLE blitzpay.merchant_menu_languages
    ADD COLUMN flag_code VARCHAR(2),
    ADD COLUMN direction VARCHAR(3) NOT NULL DEFAULT 'ltr',
    ADD COLUMN admin_ui_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN menu_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT chk_merchant_menu_languages_direction CHECK (direction IN ('ltr', 'rtl')),
    ADD CONSTRAINT chk_merchant_menu_languages_flag CHECK (flag_code IS NULL OR flag_code ~ '^[A-Z]{2}$');

UPDATE blitzpay.merchant_menu_languages
SET direction = 'rtl'
WHERE code IN ('ar', 'fa', 'he', 'ur', 'yi', 'ps', 'dv', 'sd', 'ug');

-- Enable only UI languages with translation bundles and flag assets shipped in the dashboard.
UPDATE blitzpay.merchant_menu_languages
SET admin_ui_enabled = TRUE,
    flag_code = CASE code WHEN 'en' THEN 'GB' WHEN 'de' THEN 'DE' WHEN 'ar' THEN 'SA' END
WHERE code IN ('en', 'de', 'ar');

-- rollback ALTER TABLE blitzpay.merchant_menu_languages DROP CONSTRAINT chk_merchant_menu_languages_flag;
-- rollback ALTER TABLE blitzpay.merchant_menu_languages DROP CONSTRAINT chk_merchant_menu_languages_direction;
-- rollback ALTER TABLE blitzpay.merchant_menu_languages DROP COLUMN menu_enabled, DROP COLUMN admin_ui_enabled, DROP COLUMN direction, DROP COLUMN flag_code;
