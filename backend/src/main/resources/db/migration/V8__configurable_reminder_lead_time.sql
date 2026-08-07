-- V8: configurable reminder lead time (minutes before due) per task.

ALTER TABLE routine_templates
  ADD COLUMN IF NOT EXISTS reminder_before_minutes INTEGER NOT NULL DEFAULT 30;

ALTER TABLE routine_runs
  ADD COLUMN IF NOT EXISTS reminder_before_minutes INTEGER;
