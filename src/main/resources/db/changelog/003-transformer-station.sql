--liquibase formatted sql

--changeset blackoutradar:009-create-transformer-station
CREATE TABLE transformer_station (
    id UUID NOT NULL,
    name VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_transformer_station PRIMARY KEY (id),
    CONSTRAINT uq_transformer_station_name UNIQUE (name)
);
