-- liquibase formatted sql

-- changeset mehdi:20261108-003-storefront-themes
-- Storefront themes become data: colours per light/dark mode in `tokens`, layout in `style`.
-- merchants.storefront_theme (from 20261108-002) now references a theme code.
CREATE TABLE blitzpay.storefront_themes (
    code         VARCHAR(32)  PRIMARY KEY,
    display_name VARCHAR(64)  NOT NULL,
    style        VARCHAR(16)  NOT NULL,
    tokens       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status       VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_storefront_themes_style CHECK (style IN ('STANDARD', 'CLASSIC')),
    CONSTRAINT ck_storefront_themes_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

INSERT INTO blitzpay.storefront_themes (code, display_name, style, tokens, status) VALUES
('DEFAULT', 'Standard', 'STANDARD', '{}'::jsonb, 'PUBLISHED'),
('HERITAGE', 'Heritage', 'CLASSIC', '{
  "light": {"ink": "#29231b", "muted": "#756754", "primary": "#926b1d", "mint": "#f0e6d2", "stroke": "#e4d8c3",
            "bg": "#fff9ef", "warm": "#f7efdf", "surface": "#fffdf8", "action": "#634a23",
            "action-hover": "#49351a", "positive": "#634a23", "glow": "#f0e3c9"},
  "dark":  {"ink": "#f7edde", "muted": "#c4b399", "primary": "#dbb771", "mint": "#3b2e1e", "stroke": "#564633",
            "bg": "#211b15", "warm": "#30261c", "surface": "#2b231b", "action": "#785627",
            "action-hover": "#8b642c", "positive": "#e7c68b", "glow": "#392b1d"}
}'::jsonb, 'PUBLISHED');

UPDATE blitzpay.merchants SET storefront_theme = 'DEFAULT'
WHERE storefront_theme NOT IN (SELECT code FROM blitzpay.storefront_themes);

ALTER TABLE blitzpay.merchants
    ADD CONSTRAINT fk_merchants_storefront_theme
    FOREIGN KEY (storefront_theme) REFERENCES blitzpay.storefront_themes (code) ON DELETE RESTRICT;
-- rollback ALTER TABLE blitzpay.merchants DROP CONSTRAINT fk_merchants_storefront_theme;
-- rollback DROP TABLE blitzpay.storefront_themes;
