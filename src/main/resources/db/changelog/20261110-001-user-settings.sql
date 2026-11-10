-- liquibase formatted sql

-- changeset codex:20261110-001-user-settings
CREATE TABLE blitzpay.usersettings_preferences (
    user_id VARCHAR(512) NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, setting_key),
    CONSTRAINT ck_usersettings_app_language CHECK (
        setting_key NOT IN ('shopperAppLanguage', 'merchantAppLanguage') OR setting_value IN ('en', 'de')
    )
);
-- rollback DROP TABLE blitzpay.usersettings_preferences;
