CREATE SCHEMA blitzpay;
CREATE TABLE blitzpay.merchant_menu_languages (
    code VARCHAR(8) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    native_name VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO blitzpay.merchant_menu_languages(code, display_name, native_name)
VALUES ('de', 'German', 'Deutsch'), ('en', 'English', 'English'),
       ('ar', 'Arabic', 'Arabic'), ('fr', 'French', 'French');
CREATE TABLE blitzpay.merchant_supported_menu_languages (
    merchant_id VARCHAR(20), language VARCHAR(8) REFERENCES blitzpay.merchant_menu_languages(code)
);
INSERT INTO blitzpay.merchant_supported_menu_languages VALUES ('merchant-1', 'ar');
CREATE TABLE blitzpay.merchant_product_translations (
    product_id VARCHAR(20), language VARCHAR(8) REFERENCES blitzpay.merchant_menu_languages(code),
    name VARCHAR(255), description VARCHAR(2000)
);
INSERT INTO blitzpay.merchant_product_translations
VALUES ('product-1', 'ar', 'Translated name', 'Translated description');
