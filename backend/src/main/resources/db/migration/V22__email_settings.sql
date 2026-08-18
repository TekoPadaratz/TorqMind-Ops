-- V22: DB-backed, UI-editable SMTP/email profile (singleton). Password stored encrypted at rest.
CREATE TABLE IF NOT EXISTS email_settings (
    id INT PRIMARY KEY DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    host VARCHAR(255),
    port INT NOT NULL DEFAULT 587,
    username VARCHAR(255),
    password_encrypted VARCHAR(512),
    use_tls BOOLEAN NOT NULL DEFAULT TRUE,
    use_ssl BOOLEAN NOT NULL DEFAULT FALSE,
    from_email VARCHAR(255),
    from_name VARCHAR(120) NOT NULL DEFAULT 'TorqMind Ops',
    updated_at TIMESTAMPTZ,
    CONSTRAINT email_settings_singleton CHECK (id = 1)
);
INSERT INTO email_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
