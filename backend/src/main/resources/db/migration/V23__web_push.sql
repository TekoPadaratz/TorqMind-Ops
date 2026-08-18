-- V23: Web Push (VAPID keypair singleton + per-device push subscriptions).
CREATE TABLE IF NOT EXISTS push_vapid (
    id INT PRIMARY KEY DEFAULT 1,
    public_key VARCHAR(255) NOT NULL,
    private_key VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT push_vapid_singleton CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    endpoint VARCHAR(1024) NOT NULL,
    p256dh VARCHAR(255) NOT NULL,
    auth VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_push_subscriptions_endpoint UNIQUE (endpoint)
);
CREATE INDEX IF NOT EXISTS idx_push_subscriptions_user ON push_subscriptions (user_id);
