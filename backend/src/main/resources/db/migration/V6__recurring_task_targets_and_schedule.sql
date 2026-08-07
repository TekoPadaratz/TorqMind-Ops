-- V6: recurring task with target (for whom) and notification time + scheduling.

ALTER TABLE routine_templates
  ADD COLUMN IF NOT EXISTS target_type VARCHAR(20) NOT NULL DEFAULT 'USER',
  ADD COLUMN IF NOT EXISTS target_role VARCHAR(20),
  ADD COLUMN IF NOT EXISTS target_sector_id BIGINT,
  ADD COLUMN IF NOT EXISTS target_user_id UUID,
  ADD COLUMN IF NOT EXISTS notify_time TIME,
  ADD COLUMN IF NOT EXISTS last_generated_on DATE;

CREATE INDEX IF NOT EXISTS ix_routine_templates_schedule
  ON routine_templates (is_active, notify_time)
  WHERE notify_time IS NOT NULL;
