--liquibase formatted sql

--changeset blackoutradar:019-create-notification-channel
CREATE TABLE notification_channel (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR NOT NULL,
    destination VARCHAR NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notification_channel PRIMARY KEY (id),
    CONSTRAINT uq_notification_channel_user_type_destination UNIQUE (user_id, type, destination),
    CONSTRAINT fk_notification_channel_user FOREIGN KEY (user_id)
        REFERENCES "user" (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE INDEX idx_notification_channel_user_id ON notification_channel (user_id);
