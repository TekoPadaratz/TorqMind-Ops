-- V20: parametrizable multi-item checklists for routines (per-company toggle + template items + run item state).
ALTER TABLE company_settings ADD COLUMN IF NOT EXISTS checklists_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS routine_checklist_items (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES routine_templates(id) ON DELETE CASCADE,
    position INT NOT NULL DEFAULT 0,
    label VARCHAR(300) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_checklist_items_template ON routine_checklist_items(template_id);

CREATE TABLE IF NOT EXISTS routine_run_checklist_items (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES routine_runs(id) ON DELETE CASCADE,
    template_item_id BIGINT,
    position INT NOT NULL DEFAULT 0,
    label VARCHAR(300) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    checked_by UUID,
    checked_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_run_checklist_items_run ON routine_run_checklist_items(run_id);
