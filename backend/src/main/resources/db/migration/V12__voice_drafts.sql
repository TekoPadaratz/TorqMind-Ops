-- Rascunhos de comando por voz (confirmação obrigatória; sem áudio persistido).
CREATE TABLE voice_drafts (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    company_id BIGINT,
    branch_id BIGINT,
    status VARCHAR(40) NOT NULL,
    action VARCHAR(40),
    schema_version VARCHAR(16) NOT NULL DEFAULT '1',
    transcript TEXT,
    intent_json TEXT NOT NULL,
    resolved_json TEXT,
    preview_text TEXT,
    error_message TEXT,
    correlation_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(80),
    result_entity_type VARCHAR(40),
    result_entity_id BIGINT,
    result_json TEXT,
    confirmed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_voice_drafts_actor_created ON voice_drafts (actor_user_id, created_at DESC);
CREATE INDEX idx_voice_drafts_status_expires ON voice_drafts (status, expires_at);

CREATE UNIQUE INDEX uq_voice_drafts_idempotency
    ON voice_drafts (actor_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND status = 'CONFIRMED';
