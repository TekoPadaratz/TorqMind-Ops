-- V15: controle de cadastro e senha. Sem recuperação por e-mail.
-- password_epoch invalida JWT anterior após troca/reset.

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS password_epoch INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

CREATE TABLE password_change_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES auth_users(id) ON DELETE SET NULL,
    action VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_password_change_action CHECK (action IN ('CREATED', 'SELF_CHANGE', 'ADMIN_RESET'))
);

CREATE INDEX ix_password_change_events_user ON password_change_events (user_id, created_at DESC);
