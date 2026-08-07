-- V4: setores + escopo multi-tenant de usuario para cadastros administrativos.

CREATE TABLE IF NOT EXISTS sectors (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  branch_id BIGINT REFERENCES branches(id) ON DELETE SET NULL,
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_sectors_company ON sectors(company_id);

ALTER TABLE auth_users
  ADD COLUMN IF NOT EXISTS company_id BIGINT,
  ADD COLUMN IF NOT EXISTS branch_id BIGINT,
  ADD COLUMN IF NOT EXISTS sector_id BIGINT;

INSERT INTO sectors (company_id, branch_id, name)
SELECT c.id,
       (SELECT id FROM branches WHERE company_id = c.id ORDER BY id LIMIT 1),
       s.name
FROM companies c
CROSS JOIN (VALUES ('Pista'), ('Loja de Conveniencia'), ('Manutencao')) AS s(name)
WHERE c.name = 'Rede Demonstracao';
