-- Aprendizado de frases por empresa (apos confirmacao bem-sucedida).
CREATE TABLE voice_phrase_learnings (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    branch_id BIGINT REFERENCES branches(id) ON DELETE SET NULL,
    actor_user_id UUID NOT NULL,
    phrase_normalized VARCHAR(500) NOT NULL,
    learning_type VARCHAR(40) NOT NULL,
    action VARCHAR(40),
    field_name VARCHAR(80),
    field_value TEXT,
    intent_snapshot TEXT,
    hit_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_voice_learning_type CHECK (learning_type IN ('INTENT', 'SLOT'))
);

CREATE UNIQUE INDEX uq_voice_phrase_learning
    ON voice_phrase_learnings (company_id, phrase_normalized, learning_type, COALESCE(field_name, ''), COALESCE(action, ''));

CREATE INDEX idx_voice_phrase_learning_company ON voice_phrase_learnings (company_id, last_used_at DESC);

ALTER TABLE voice_drafts ADD COLUMN IF NOT EXISTS last_clarification_transcript TEXT;
