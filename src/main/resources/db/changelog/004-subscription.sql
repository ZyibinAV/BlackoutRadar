--liquibase formatted sql

--changeset blackoutradar:010-create-subscription
CREATE TABLE subscription (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    address_id UUID NOT NULL,
    monitoring_start TIMESTAMP WITH TIME ZONE NOT NULL,
    monitoring_end TIMESTAMP WITH TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    service_access_until TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription PRIMARY KEY (id),
    CONSTRAINT ck_subscription_monitoring_range CHECK (monitoring_start < monitoring_end),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id)
        REFERENCES "user" (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_subscription_address FOREIGN KEY (address_id)
        REFERENCES address (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_subscription_user_id_is_active ON subscription (user_id, is_active);
CREATE INDEX idx_subscription_address_id_is_active ON subscription (address_id, is_active);
CREATE INDEX idx_subscription_monitoring_range ON subscription (monitoring_start, monitoring_end);

--changeset blackoutradar:011-create-subscription-transformer-station
CREATE TABLE subscription_transformer_station (
    id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    transformer_station_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_transformer_station PRIMARY KEY (id),
    CONSTRAINT uq_subscription_transformer_station UNIQUE (subscription_id, transformer_station_id),
    CONSTRAINT fk_sts_subscription FOREIGN KEY (subscription_id)
        REFERENCES subscription (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_sts_transformer_station FOREIGN KEY (transformer_station_id)
        REFERENCES transformer_station (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_sts_subscription_id ON subscription_transformer_station (subscription_id);
CREATE INDEX idx_sts_transformer_station_id ON subscription_transformer_station (transformer_station_id);
