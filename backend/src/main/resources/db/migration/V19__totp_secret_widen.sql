-- V19: widen totp_secret to hold the encrypted (AES-GCM base64) value.
ALTER TABLE auth_users ALTER COLUMN totp_secret TYPE VARCHAR(255);
