-- V18: opt-in TOTP (2FA) per user. Default flow (no 2FA) stays unchanged.
ALTER TABLE auth_users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(64);
ALTER TABLE auth_users ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
