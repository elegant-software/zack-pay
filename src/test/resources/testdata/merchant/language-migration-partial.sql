ALTER TABLE blitzpay.merchant_menu_languages ADD COLUMN flag_code VARCHAR(2);
UPDATE blitzpay.merchant_menu_languages SET flag_code = 'FR' WHERE code = 'fr';
