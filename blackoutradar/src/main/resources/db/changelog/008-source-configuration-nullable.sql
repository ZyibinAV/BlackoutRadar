--liquibase formatted sql

--changeset blackoutradar:016-make-source-configuration-nullable
ALTER TABLE source ALTER COLUMN configuration DROP NOT NULL;