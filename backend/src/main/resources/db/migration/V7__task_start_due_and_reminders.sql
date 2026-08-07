-- V7: start/due time for tasks, weekly/monthly/once scheduling and expiry reminder flag.

ALTER TABLE routine_templates
  ADD COLUMN IF NOT EXISTS start_time TIME,
  ADD COLUMN IF NOT EXISTS due_time TIME,
  ADD COLUMN IF NOT EXISTS weekday SMALLINT,
  ADD COLUMN IF NOT EXISTS day_of_month SMALLINT,
  ADD COLUMN IF NOT EXISTS start_date DATE;

ALTER TABLE routine_runs
  ADD COLUMN IF NOT EXISTS expiry_reminded BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE routine_templates SET start_time = notify_time WHERE start_time IS NULL AND notify_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_routine_templates_start
  ON routine_templates (is_active, start_time)
  WHERE start_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_routine_runs_due_open
  ON routine_runs (status, due_at)
  WHERE due_at IS NOT NULL;
