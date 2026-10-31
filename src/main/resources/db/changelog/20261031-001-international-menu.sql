-- liquibase formatted sql

-- changeset mehdi:20261031-001-menu-language-catalogue
CREATE TABLE blitzpay.merchant_menu_languages (
    code         VARCHAR(8)   NOT NULL,
    display_name VARCHAR(64)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_merchant_menu_languages PRIMARY KEY (code)
);

INSERT INTO blitzpay.merchant_menu_languages (code, display_name, active)
VALUES
    ('de', 'Deutsch', TRUE),
    ('en', 'English', TRUE),
    ('fr', 'Français', TRUE),
    ('it', 'Italiano', TRUE),
    ('es', 'Español', TRUE),
    ('nl', 'Nederlands', TRUE),
    ('pl', 'Polski', TRUE),
    ('tr', 'Türkçe', TRUE),
    ('ar', 'العربية', TRUE);

-- rollback DROP TABLE blitzpay.merchant_menu_languages;

-- changeset mehdi:20261031-002-international-menu-offering
INSERT INTO blitzpay.merchant_offerings (code, display_name, active)
VALUES ('INTERNATIONAL_MENU', 'International Menu', TRUE)
ON CONFLICT (code) DO NOTHING;

-- rollback DELETE FROM blitzpay.merchant_application_offerings WHERE offering_code = 'INTERNATIONAL_MENU';
-- rollback DELETE FROM blitzpay.merchant_offerings WHERE code = 'INTERNATIONAL_MENU';

-- changeset mehdi:20261031-003-merchant-original-menu-language
ALTER TABLE blitzpay.merchants
    ADD COLUMN original_menu_language VARCHAR(8),
    ADD CONSTRAINT fk_merchants_merchant_menu_languages
        FOREIGN KEY (original_menu_language)
        REFERENCES blitzpay.merchant_menu_languages (code)
        ON DELETE RESTRICT;

UPDATE blitzpay.merchants
SET original_menu_language = CASE WHEN upper(operating_country) IN ('DE', 'AT') THEN 'de' ELSE 'en' END
WHERE original_menu_language IS NULL;

-- rollback ALTER TABLE blitzpay.merchants DROP COLUMN original_menu_language;

-- changeset mehdi:20261031-004-merchant-supported-menu-languages
CREATE TABLE blitzpay.merchant_supported_menu_languages (
    merchant_id UUID        NOT NULL,
    language    VARCHAR(8)  NOT NULL,
    added_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_merchant_supported_menu_languages PRIMARY KEY (merchant_id, language),
    CONSTRAINT fk_merchant_supported_menu_languages_merchants
        FOREIGN KEY (merchant_id) REFERENCES blitzpay.merchants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_merchant_supported_menu_languages_merchant_menu_languages
        FOREIGN KEY (language) REFERENCES blitzpay.merchant_menu_languages (code) ON DELETE RESTRICT
);

-- rollback DROP TABLE blitzpay.merchant_supported_menu_languages;

-- changeset mehdi:20261031-005-merchant-product-translations
CREATE TABLE blitzpay.merchant_product_translations (
    product_id  UUID          NOT NULL,
    language    VARCHAR(8)    NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description VARCHAR(2000),
    origin      VARCHAR(16)   NOT NULL,
    source_hash VARCHAR(64)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_merchant_product_translations PRIMARY KEY (product_id, language),
    CONSTRAINT fk_merchant_product_translations_merchant_products
        FOREIGN KEY (product_id) REFERENCES blitzpay.merchant_products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_merchant_product_translations_merchant_menu_languages
        FOREIGN KEY (language) REFERENCES blitzpay.merchant_menu_languages (code) ON DELETE RESTRICT,
    CONSTRAINT chk_merchant_product_translations_origin CHECK (origin IN ('MACHINE', 'MERCHANT'))
);

-- rollback DROP TABLE blitzpay.merchant_product_translations;

-- changeset mehdi:20261031-006-merchant-product-category-translations
CREATE TABLE blitzpay.merchant_product_category_translations (
    category_id UUID         NOT NULL,
    language    VARCHAR(8)   NOT NULL,
    name        VARCHAR(100) NOT NULL,
    origin      VARCHAR(16)  NOT NULL,
    source_hash VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_merchant_product_category_translations PRIMARY KEY (category_id, language),
    CONSTRAINT fk_merchant_product_category_translations_merchant_product_categories
        FOREIGN KEY (category_id) REFERENCES blitzpay.merchant_product_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_merchant_product_category_translations_merchant_menu_languages
        FOREIGN KEY (language) REFERENCES blitzpay.merchant_menu_languages (code) ON DELETE RESTRICT,
    CONSTRAINT chk_merchant_product_category_translations_origin CHECK (origin IN ('MACHINE', 'MERCHANT'))
);

-- rollback DROP TABLE blitzpay.merchant_product_category_translations;

-- changeset mehdi:20261031-007-menu-language-iso-639-1-catalogue
-- Standard ISO 639-1 codes for the menu language catalogue, with English and native names for the admin dropdown.
ALTER TABLE blitzpay.merchant_menu_languages
    ADD COLUMN native_name VARCHAR(64);

INSERT INTO blitzpay.merchant_menu_languages (code, display_name, native_name, active)
VALUES
    ('ar', 'Arabic', 'العربية', TRUE),
    ('bg', 'Bulgarian', 'Български', TRUE),
    ('bn', 'Bengali', 'বাংলা', TRUE),
    ('bs', 'Bosnian', 'Bosanski', TRUE),
    ('ca', 'Catalan', 'Català', TRUE),
    ('cs', 'Czech', 'Čeština', TRUE),
    ('da', 'Danish', 'Dansk', TRUE),
    ('de', 'German', 'Deutsch', TRUE),
    ('el', 'Greek', 'Ελληνικά', TRUE),
    ('en', 'English', 'English', TRUE),
    ('es', 'Spanish', 'Español', TRUE),
    ('et', 'Estonian', 'Eesti', TRUE),
    ('fa', 'Persian', 'فارسی', TRUE),
    ('fi', 'Finnish', 'Suomi', TRUE),
    ('fr', 'French', 'Français', TRUE),
    ('he', 'Hebrew', 'עברית', TRUE),
    ('hi', 'Hindi', 'हिन्दी', TRUE),
    ('hr', 'Croatian', 'Hrvatski', TRUE),
    ('hu', 'Hungarian', 'Magyar', TRUE),
    ('id', 'Indonesian', 'Bahasa Indonesia', TRUE),
    ('it', 'Italian', 'Italiano', TRUE),
    ('ja', 'Japanese', '日本語', TRUE),
    ('ko', 'Korean', '한국어', TRUE),
    ('ku', 'Kurdish', 'Kurdî', TRUE),
    ('lt', 'Lithuanian', 'Lietuvių', TRUE),
    ('lv', 'Latvian', 'Latviešu', TRUE),
    ('mk', 'Macedonian', 'Македонски', TRUE),
    ('ms', 'Malay', 'Bahasa Melayu', TRUE),
    ('nl', 'Dutch', 'Nederlands', TRUE),
    ('no', 'Norwegian', 'Norsk', TRUE),
    ('pl', 'Polish', 'Polski', TRUE),
    ('pt', 'Portuguese', 'Português', TRUE),
    ('ro', 'Romanian', 'Română', TRUE),
    ('ru', 'Russian', 'Русский', TRUE),
    ('sk', 'Slovak', 'Slovenčina', TRUE),
    ('sl', 'Slovenian', 'Slovenščina', TRUE),
    ('sq', 'Albanian', 'Shqip', TRUE),
    ('sr', 'Serbian', 'Српски', TRUE),
    ('sv', 'Swedish', 'Svenska', TRUE),
    ('th', 'Thai', 'ไทย', TRUE),
    ('tr', 'Turkish', 'Türkçe', TRUE),
    ('uk', 'Ukrainian', 'Українська', TRUE),
    ('ur', 'Urdu', 'اردو', TRUE),
    ('vi', 'Vietnamese', 'Tiếng Việt', TRUE),
    ('zh', 'Chinese', '中文', TRUE)
ON CONFLICT (code) DO UPDATE
    SET display_name = EXCLUDED.display_name,
        native_name  = EXCLUDED.native_name;

UPDATE blitzpay.merchant_menu_languages SET native_name = display_name WHERE native_name IS NULL;

ALTER TABLE blitzpay.merchant_menu_languages
    ALTER COLUMN native_name SET NOT NULL;

-- rollback ALTER TABLE blitzpay.merchant_menu_languages DROP COLUMN native_name;
