-- V3: evolui schema para persistencia completa + seed inicial de demonstracao.

-- 1) Converte enums nativos para varchar (mapeamento JPA robusto).
ALTER TABLE routine_runs ALTER COLUMN status DROP DEFAULT;
ALTER TABLE routine_runs ALTER COLUMN status TYPE VARCHAR(30) USING status::text;
ALTER TABLE routine_runs ALTER COLUMN status SET DEFAULT 'PENDENTE';

ALTER TABLE occurrences ALTER COLUMN status DROP DEFAULT;
ALTER TABLE occurrences ALTER COLUMN status TYPE VARCHAR(30) USING status::text;
ALTER TABLE occurrences ALTER COLUMN status SET DEFAULT 'ABERTA';

DROP TYPE IF EXISTS routine_status;
DROP TYPE IF EXISTS occurrence_status;

-- 2) Novas colunas de dominio.
ALTER TABLE routine_templates
  ADD COLUMN IF NOT EXISTS description TEXT,
  ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS created_by UUID;

ALTER TABLE routine_runs
  ADD COLUMN IF NOT EXISTS company_id BIGINT,
  ADD COLUMN IF NOT EXISTS branch_id BIGINT,
  ADD COLUMN IF NOT EXISTS assigned_user_id UUID,
  ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS execution_comment TEXT,
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE occurrences
  ADD COLUMN IF NOT EXISTS priority VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
  ADD COLUMN IF NOT EXISTS opened_by UUID,
  ADD COLUMN IF NOT EXISTS assignee_user_id UUID;

ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

-- 3) Backfill e integridade.
UPDATE routine_runs r
SET company_id = t.company_id,
    branch_id = t.branch_id
FROM routine_templates t
WHERE r.template_id = t.id AND r.company_id IS NULL;

ALTER TABLE routine_runs ALTER COLUMN company_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_routine_runs_company ON routine_runs(company_id, status, due_at);
CREATE INDEX IF NOT EXISTS ix_occurrences_company ON occurrences(company_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_notifications_recipient ON notifications(recipient_user_id, read_at, created_at DESC);

-- 4) Seed de demonstracao (empresa, filiais, rotina, execucao e ocorrencia).
INSERT INTO companies (name) VALUES ('Rede Demonstracao');

INSERT INTO branches (company_id, name)
SELECT c.id, b.name
FROM companies c
CROSS JOIN (VALUES ('Posto Matriz'), ('Posto Filial Norte'), ('Posto Filial Sul')) AS b(name)
WHERE c.name = 'Rede Demonstracao';

INSERT INTO routine_templates (company_id, branch_id, title, description, recurrence_rule, requires_photo, requires_comment, is_active)
SELECT c.id,
       (SELECT id FROM branches WHERE company_id = c.id ORDER BY id LIMIT 1),
       'Aferir bombas',
       'Afericao semanal das bombas de combustivel.',
       'WEEKLY',
       TRUE,
       TRUE,
       TRUE
FROM companies c
WHERE c.name = 'Rede Demonstracao';

INSERT INTO routine_runs (template_id, company_id, branch_id, status, scheduled_for, due_at)
SELECT t.id, t.company_id, t.branch_id, 'PENDENTE', NOW(), NOW() + INTERVAL '1 day'
FROM routine_templates t
WHERE t.title = 'Aferir bombas';

INSERT INTO occurrences (company_id, branch_id, title, description, status, priority)
SELECT c.id,
       (SELECT id FROM branches WHERE company_id = c.id ORDER BY id LIMIT 1),
       'Ar-condicionado com defeito',
       'O ar-condicionado da loja de conveniencia nao esta gelando.',
       'ABERTA',
       'ALTA'
FROM companies c
WHERE c.name = 'Rede Demonstracao';
