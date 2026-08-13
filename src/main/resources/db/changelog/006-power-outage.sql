--liquibase formatted sql

--changeset blackoutradar:013-create-power-outage
CREATE TABLE power_outage (
    id UUID NOT NULL,
    source_id UUID NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_power_outage PRIMARY KEY (id),
    CONSTRAINT ck_power_outage_time_range CHECK (start_time < end_time),
    CONSTRAINT fk_power_outage_source FOREIGN KEY (source_id)
        REFERENCES source (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_power_outage_source_id ON power_outage (source_id);
CREATE INDEX idx_power_outage_start_time ON power_outage (start_time);
CREATE INDEX idx_power_outage_end_time ON power_outage (end_time);
CREATE INDEX idx_power_outage_status ON power_outage (status);
CREATE INDEX idx_power_outage_time_range ON power_outage (start_time, end_time);

--changeset blackoutradar:014-create-power-outage-address
CREATE TABLE power_outage_address (
    id UUID NOT NULL,
    power_outage_id UUID NOT NULL,
    address_id UUID NOT NULL,
    transformer_station_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_power_outage_address PRIMARY KEY (id),
    CONSTRAINT uq_power_outage_address UNIQUE (power_outage_id, address_id),
    CONSTRAINT fk_poa_power_outage FOREIGN KEY (power_outage_id)
        REFERENCES power_outage (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_poa_address FOREIGN KEY (address_id)
        REFERENCES address (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_poa_transformer_station FOREIGN KEY (transformer_station_id)
        REFERENCES transformer_station (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_poa_power_outage_id ON power_outage_address (power_outage_id);
CREATE INDEX idx_poa_address_id ON power_outage_address (address_id);
CREATE INDEX idx_poa_transformer_station_id ON power_outage_address (transformer_station_id);
