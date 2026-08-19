-- V25: webhooks de saida por empresa (segredo HMAC cifrado em repouso).
CREATE TABLE IF NOT EXISTS webhooks (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    url VARCHAR(1024) NOT NULL,
    secret VARCHAR(512) NOT NULL,
    events VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    last_status VARCHAR(64),
    last_attempt_at TIMESTAMPTZ,
    failure_count INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_webhooks_company ON webhooks (company_id);
