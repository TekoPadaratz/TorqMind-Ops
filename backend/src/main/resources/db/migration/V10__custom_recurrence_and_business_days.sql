-- Recorrência personalizada (dias do mês) e filtro de dias úteis
ALTER TABLE routine_templates
    ADD COLUMN IF NOT EXISTS custom_days VARCHAR(120),
    ADD COLUMN IF NOT EXISTS business_days_only BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN routine_templates.custom_days IS
    'Dias do mês para recorrência CUSTOM, separados por vírgula (ex: 1,15,28)';
COMMENT ON COLUMN routine_templates.business_days_only IS
    'Se true, dia em sáb/dom adia para o próximo dia útil (MONTHLY e CUSTOM)';
