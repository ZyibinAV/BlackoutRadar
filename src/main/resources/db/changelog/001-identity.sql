--liquibase formatted sql

--changeset blackoutradar:001-create-user
CREATE TABLE "user" (
    id UUID NOT NULL,
    email VARCHAR NOT NULL,
    password_hash VARCHAR,
    role VARCHAR NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    nickname VARCHAR,
    about TEXT,
    avatar_key VARCHAR,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user PRIMARY KEY (id),
    CONSTRAINT uq_user_email UNIQUE (email)
);

--changeset blackoutradar:002-create-refresh-token
CREATE TABLE refresh_token (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES "user" (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);
