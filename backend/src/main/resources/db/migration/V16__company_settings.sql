-- V16: per-company operational settings (evidence and reminder defaults).
CREATE TABLE IF NOT EXISTS company_settings (
    company_id BIGINT PRIMARY KEY REFERENCES companies(id),
    require_photo_on_complete BOOLEAN NOT NULL DEFAULT TRUE,
    require_comment_on_complete BOOLEAN NOT NULL DEFAULT TRUE,
    default_reminder_minutes INTEGER NOT NULL DEFAULT 15,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
