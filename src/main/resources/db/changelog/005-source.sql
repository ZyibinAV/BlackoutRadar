--liquibase formatted sql

--changeset blackoutradar:012-create-source
CREATE TABLE source (
    id UUID NOT NULL,
    name VARCHAR NOT NULL,
    source_type VARCHAR NOT NULL,
    provider_type VARCHAR NOT NULL,
    configuration JSONB NOT NULL,
    schedule VARCHAR NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_source PRIMARY KEY (id),
    CONSTRAINT uq_source_name UNIQUE (name)
);

CREATE INDEX idx_source_is_active ON source (is_active);
CREATE INDEX idx_source_source_type ON source (source_type);
