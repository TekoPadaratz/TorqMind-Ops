-- V17: optional geolocation stamp (lat/lng) on task attachments (photo evidence).
ALTER TABLE task_attachments ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE task_attachments ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
