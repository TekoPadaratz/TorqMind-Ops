-- Multi-tenant Drive: IDs de pasta no Google Drive por empresa/filial
ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS drive_folder_id VARCHAR(80);

ALTER TABLE branches
    ADD COLUMN IF NOT EXISTS drive_folder_id VARCHAR(80);
