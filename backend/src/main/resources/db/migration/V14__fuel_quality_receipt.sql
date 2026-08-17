-- V14: cadastro de posto (razão social, CNPJ, endereço) e ocorrência
-- estruturada de análise de qualidade no recebimento de combustível.
-- Rascunho = ABERTA; finalizada = ENCERRADA. Snapshot fica na análise.

ALTER TABLE companies
    ADD COLUMN legal_name VARCHAR(180),
    ADD COLUMN cnpj VARCHAR(20),
    ADD COLUMN address_street VARCHAR(180),
    ADD COLUMN address_number VARCHAR(30),
    ADD COLUMN address_complement VARCHAR(80),
    ADD COLUMN address_neighborhood VARCHAR(80),
    ADD COLUMN address_city VARCHAR(80),
    ADD COLUMN address_state VARCHAR(2),
    ADD COLUMN address_postal_code VARCHAR(12);

ALTER TABLE branches
    ADD COLUMN legal_name VARCHAR(180),
    ADD COLUMN cnpj VARCHAR(20),
    ADD COLUMN address_street VARCHAR(180),
    ADD COLUMN address_number VARCHAR(30),
    ADD COLUMN address_complement VARCHAR(80),
    ADD COLUMN address_neighborhood VARCHAR(80),
    ADD COLUMN address_city VARCHAR(80),
    ADD COLUMN address_state VARCHAR(2),
    ADD COLUMN address_postal_code VARCHAR(12);

ALTER TABLE occurrences
    ADD COLUMN kind VARCHAR(40) NOT NULL DEFAULT 'GENERIC',
    ADD COLUMN finalized_at TIMESTAMPTZ,
    ADD COLUMN finalized_by UUID,
    ADD COLUMN document_attachment_id BIGINT;

ALTER TABLE occurrences
    ADD CONSTRAINT ck_occurrences_kind CHECK (kind IN ('GENERIC', 'FUEL_QUALITY_RECEIPT')) NOT VALID,
    ADD CONSTRAINT fk_occurrences_finalized_by FOREIGN KEY (finalized_by) REFERENCES auth_users(id) NOT VALID,
    ADD CONSTRAINT fk_occurrences_document FOREIGN KEY (document_attachment_id) REFERENCES task_attachments(id) NOT VALID;

CREATE TABLE fuel_quality_analyses (
    occurrence_id BIGINT PRIMARY KEY REFERENCES occurrences(id) ON DELETE CASCADE,
    fuel VARCHAR(40) NOT NULL,
    station_name VARCHAR(180),
    station_legal_name VARCHAR(180),
    station_cnpj VARCHAR(20),
    station_address_street VARCHAR(180),
    station_address_number VARCHAR(30),
    station_address_complement VARCHAR(80),
    station_address_neighborhood VARCHAR(80),
    station_address_city VARCHAR(80),
    station_address_state VARCHAR(2),
    station_address_postal_code VARCHAR(12),
    collection_date DATE,
    received_volume VARCHAR(40),
    distributor_name VARCHAR(180),
    distributor_cnpj VARCHAR(20),
    transporter VARCHAR(180),
    product_nfe VARCHAR(80),
    truck_plate VARCHAR(20),
    trailer_plate VARCHAR(20),
    driver_name VARCHAR(180),
    driver_document VARCHAR(40),
    analyst_name VARCHAR(180),
    appearance VARCHAR(80),
    color VARCHAR(80),
    specific_mass_20c VARCHAR(40),
    gasoline_alcohol_content VARCHAR(40),
    aehc_alcohol_content VARCHAR(40),
    filled_by_name VARCHAR(180),
    filled_by_user_id UUID,
    responsible_signature_attachment_id BIGINT,
    witnesses_json TEXT,
    CONSTRAINT ck_fuel_quality_fuel CHECK (fuel IN (
        'DIESEL_S10', 'DIESEL_S500', 'ETANOL', 'GASOLINA_ADITIVADA', 'GASOLINA_COMUM'
    ))
);

CREATE INDEX ix_occurrences_kind ON occurrences (company_id, kind, created_at DESC);
