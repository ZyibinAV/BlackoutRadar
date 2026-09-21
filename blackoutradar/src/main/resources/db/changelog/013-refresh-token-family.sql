--liquibase formatted sql

--changeset blackoutradar:013-refresh-token-family
ALTER TABLE refresh_token
    ADD COLUMN family_id UUID;

UPDATE refresh_token
   SET family_id = id
 WHERE family_id IS NULL;

ALTER TABLE refresh_token
    ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_refresh_token_family_id
    ON refresh_token (family_id);
