--liquibase formatted sql

--changeset blackoutradar:003-create-region
CREATE TABLE region (
    id UUID NOT NULL,
    name VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_region PRIMARY KEY (id),
    CONSTRAINT uq_region_name UNIQUE (name)
);

--changeset blackoutradar:004-create-regional-district
CREATE TABLE regional_district (
    id UUID NOT NULL,
    region_id UUID NOT NULL,
    type VARCHAR NOT NULL,
    name VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_regional_district PRIMARY KEY (id),
    CONSTRAINT uq_regional_district_region_type_name UNIQUE (region_id, type, name),
    CONSTRAINT fk_regional_district_region FOREIGN KEY (region_id)
        REFERENCES region (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Supporting unique index required to back the composite FK
-- fk_city_regional_district_same_region.
CREATE UNIQUE INDEX uq_regional_district_region_id_id
    ON regional_district (region_id, id);

CREATE INDEX idx_regional_district_region_id ON regional_district (region_id);

--changeset blackoutradar:005-create-city
CREATE TABLE city (
    id UUID NOT NULL,
    region_id UUID NOT NULL,
    regional_district_id UUID,
    name VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_city PRIMARY KEY (id),
    CONSTRAINT fk_city_region FOREIGN KEY (region_id)
        REFERENCES region (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_city_regional_district FOREIGN KEY (regional_district_id)
        REFERENCES regional_district (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_city_regional_district_same_region
        FOREIGN KEY (region_id, regional_district_id)
        REFERENCES regional_district (region_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Partial unique indexes for the two City membership variants.
CREATE UNIQUE INDEX uq_city_regional_district_name
    ON city (regional_district_id, name)
    WHERE regional_district_id IS NOT NULL;

CREATE UNIQUE INDEX uq_city_region_name_no_district
    ON city (region_id, name)
    WHERE regional_district_id IS NULL;

CREATE INDEX idx_city_region_id ON city (region_id);
CREATE INDEX idx_city_regional_district_id ON city (regional_district_id);

--changeset blackoutradar:006-create-city-district
CREATE TABLE city_district (
    id UUID NOT NULL,
    city_id UUID NOT NULL,
    name VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_city_district PRIMARY KEY (id),
    CONSTRAINT uq_city_district_city_name UNIQUE (city_id, name),
    CONSTRAINT fk_city_district_city FOREIGN KEY (city_id)
        REFERENCES city (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Supporting unique index required to back the composite FK
-- fk_address_city_district_city.
CREATE UNIQUE INDEX uq_city_district_city_id_id ON city_district (city_id, id);

CREATE INDEX idx_city_district_city_id ON city_district (city_id);

--changeset blackoutradar:007-create-street
CREATE TABLE street (
    id UUID NOT NULL,
    city_id UUID NOT NULL,
    type VARCHAR NOT NULL,
    canonical_name VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_street PRIMARY KEY (id),
    CONSTRAINT uq_street_city_type_name UNIQUE (city_id, type, canonical_name),
    CONSTRAINT fk_street_city FOREIGN KEY (city_id)
        REFERENCES city (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Supporting unique index required to back the composite FK
-- fk_address_street_city.
CREATE UNIQUE INDEX uq_street_city_id_id ON street (city_id, id);

CREATE INDEX idx_street_city_id ON street (city_id);
CREATE INDEX idx_street_canonical_name ON street (canonical_name);

--changeset blackoutradar:008-create-address
CREATE TABLE address (
    id UUID NOT NULL,
    city_id UUID NOT NULL,
    street_id UUID NOT NULL,
    city_district_id UUID,
    house_number VARCHAR NOT NULL,
    house_addition VARCHAR,
    canonical_house VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_address PRIMARY KEY (id),
    CONSTRAINT fk_address_street FOREIGN KEY (street_id)
        REFERENCES street (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_address_city_district FOREIGN KEY (city_district_id)
        REFERENCES city_district (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- city_id is a technical referential-integrity carrier (not part of the
    -- Domain Model). It guarantees that the CityDistrict referenced by
    -- city_district_id belongs to the same City as the referenced Street.
    CONSTRAINT fk_address_street_city
        FOREIGN KEY (city_id, street_id)
        REFERENCES street (city_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_address_city_district_city
        FOREIGN KEY (city_id, city_district_id)
        REFERENCES city_district (city_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Partial unique indexes for the two Address membership variants.
CREATE UNIQUE INDEX uq_address_street_district_house
    ON address (street_id, city_district_id, canonical_house)
    WHERE city_district_id IS NOT NULL;

CREATE UNIQUE INDEX uq_address_street_house
    ON address (street_id, canonical_house)
    WHERE city_district_id IS NULL;

CREATE INDEX idx_address_street_id ON address (street_id);
CREATE INDEX idx_address_city_district_id ON address (city_district_id);
CREATE INDEX idx_address_canonical_house ON address (canonical_house);
