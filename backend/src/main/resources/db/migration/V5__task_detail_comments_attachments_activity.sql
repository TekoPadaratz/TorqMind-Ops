-- V5: comentarios, anexos e linha do tempo de atividade para tarefas (rotinas e ocorrencias).

CREATE TABLE IF NOT EXISTS task_comments (
  id BIGSERIAL PRIMARY KEY,
  task_type VARCHAR(20) NOT NULL,
  task_id BIGINT NOT NULL,
  author_user_id UUID NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_task_comments_type CHECK (task_type IN ('ROUTINE_RUN', 'OCCURRENCE'))
);

CREATE INDEX IF NOT EXISTS ix_task_comments_task ON task_comments(task_type, task_id, created_at);

CREATE TABLE IF NOT EXISTS task_attachments (
  id BIGSERIAL PRIMARY KEY,
  task_type VARCHAR(20) NOT NULL,
  task_id BIGINT NOT NULL,
  uploaded_by UUID NOT NULL,
  storage_provider VARCHAR(40) NOT NULL,
  storage_path TEXT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  checksum_sha256 VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_task_attachments_type CHECK (task_type IN ('ROUTINE_RUN', 'OCCURRENCE'))
);

CREATE INDEX IF NOT EXISTS ix_task_attachments_task ON task_attachments(task_type, task_id, created_at);

CREATE TABLE IF NOT EXISTS task_activities (
  id BIGSERIAL PRIMARY KEY,
  task_type VARCHAR(20) NOT NULL,
  task_id BIGINT NOT NULL,
  actor_user_id UUID,
  activity_type VARCHAR(30) NOT NULL,
  from_status VARCHAR(30),
  to_status VARCHAR(30),
  message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_task_activities_type CHECK (task_type IN ('ROUTINE_RUN', 'OCCURRENCE'))
);

CREATE INDEX IF NOT EXISTS ix_task_activities_task ON task_activities(task_type, task_id, created_at);
