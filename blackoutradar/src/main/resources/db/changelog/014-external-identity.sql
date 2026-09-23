--liquibase formatted sql

--changeset blackoutradar:014-create-external-identity
CREATE TABLE external_identity (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    provider VARCHAR NOT NULL,
    provider_subject VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_external_identity PRIMARY KEY (id),
    CONSTRAINT uq_external_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_external_identity_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_external_identity_user FOREIGN KEY (user_id)
        REFERENCES "user" (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE INDEX idx_external_identity_user_id ON external_identity (user_id);
