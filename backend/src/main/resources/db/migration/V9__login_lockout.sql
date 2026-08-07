-- V9: brute-force protection - failed login counter and temporary account lock.

ALTER TABLE auth_users
  ADD COLUMN IF NOT EXISTS failed_login_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
