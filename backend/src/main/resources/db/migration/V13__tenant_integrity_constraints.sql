-- V13: protege vínculos multi-tenant também no banco. NOT VALID preserva dados legados,
-- mas passa a validar toda nova gravação imediatamente.

ALTER TABLE auth_users
    ADD CONSTRAINT fk_auth_users_company FOREIGN KEY (company_id) REFERENCES companies(id) NOT VALID,
    ADD CONSTRAINT fk_auth_users_branch FOREIGN KEY (branch_id) REFERENCES branches(id) NOT VALID,
    ADD CONSTRAINT fk_auth_users_sector FOREIGN KEY (sector_id) REFERENCES sectors(id) NOT VALID;

ALTER TABLE routine_templates
    ADD CONSTRAINT fk_routine_templates_creator FOREIGN KEY (created_by) REFERENCES auth_users(id) NOT VALID,
    ADD CONSTRAINT fk_routine_templates_target_sector FOREIGN KEY (target_sector_id) REFERENCES sectors(id) NOT VALID,
    ADD CONSTRAINT fk_routine_templates_target_user FOREIGN KEY (target_user_id) REFERENCES auth_users(id) NOT VALID,
    ADD CONSTRAINT ck_routine_templates_recurrence CHECK (recurrence_rule IN ('ONCE','DAILY','WEEKLY','MONTHLY','CUSTOM')) NOT VALID,
    ADD CONSTRAINT ck_routine_templates_target CHECK (target_type IN ('USER','SECTOR','MANAGERS','ALL')) NOT VALID,
    ADD CONSTRAINT ck_routine_templates_reminder CHECK (reminder_before_minutes BETWEEN 0 AND 1440) NOT VALID;

ALTER TABLE routine_runs
    ADD CONSTRAINT fk_routine_runs_company FOREIGN KEY (company_id) REFERENCES companies(id) NOT VALID,
    ADD CONSTRAINT fk_routine_runs_branch FOREIGN KEY (branch_id) REFERENCES branches(id) NOT VALID,
    ADD CONSTRAINT fk_routine_runs_assignee FOREIGN KEY (assigned_user_id) REFERENCES auth_users(id) NOT VALID,
    ADD CONSTRAINT ck_routine_runs_status CHECK (status IN ('PENDENTE','EM_ANDAMENTO','CONCLUIDA','ATRASADA','REJEITADA')) NOT VALID;

ALTER TABLE occurrences
    ADD CONSTRAINT fk_occurrences_opener FOREIGN KEY (opened_by) REFERENCES auth_users(id) NOT VALID,
    ADD CONSTRAINT fk_occurrences_assignee FOREIGN KEY (assignee_user_id) REFERENCES auth_users(id) NOT VALID,
    ADD CONSTRAINT ck_occurrences_status CHECK (status IN ('ABERTA','EM_ATENDIMENTO','AGUARDANDO_VALIDACAO','ENCERRADA','REJEITADA')) NOT VALID,
    ADD CONSTRAINT ck_occurrences_priority CHECK (priority IN ('BAIXA','MEDIA','ALTA','CRITICA')) NOT VALID;

ALTER TABLE task_comments
    ADD CONSTRAINT fk_task_comments_author FOREIGN KEY (author_user_id) REFERENCES auth_users(id) NOT VALID;

ALTER TABLE task_attachments
    ADD CONSTRAINT fk_task_attachments_uploader FOREIGN KEY (uploaded_by) REFERENCES auth_users(id) NOT VALID;

ALTER TABLE task_activities
    ADD CONSTRAINT fk_task_activities_actor FOREIGN KEY (actor_user_id) REFERENCES auth_users(id) NOT VALID;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES auth_users(id) NOT VALID,
    ADD CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_user_id) REFERENCES auth_users(id) NOT VALID;
