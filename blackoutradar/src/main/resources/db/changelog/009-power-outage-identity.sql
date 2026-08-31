--liquibase formatted sql

--changeset blackoutradar:017-add-power-outage-identity-columns
ALTER TABLE power_outage ADD COLUMN external_reference VARCHAR;
ALTER TABLE power_outage ADD COLUMN fallback_fingerprint VARCHAR;

--changeset blackoutradar:018-create-power-outage-identity-indexes
CREATE UNIQUE INDEX uq_power_outage_source_external ON power_outage (source_id, external_reference) WHERE external_reference IS NOT NULL;
CREATE UNIQUE INDEX uq_power_outage_source_fallback ON power_outage (source_id, fallback_fingerprint) WHERE fallback_fingerprint IS NOT NULL;
