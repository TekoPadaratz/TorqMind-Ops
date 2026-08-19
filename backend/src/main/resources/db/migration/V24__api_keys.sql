-- V24: chaves de API para acesso somente-leitura por empresa (hash em repouso).
CREATE TABLE IF NOT EXISTS api_keys (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    prefix VARCHAR(24) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT uk_api_keys_prefix UNIQUE (prefix)
);
CREATE INDEX IF NOT EXISTS idx_api_keys_company ON api_keys (company_id);
