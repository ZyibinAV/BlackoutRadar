--liquibase formatted sql

--changeset blackoutradar:020-create-notification-delivery
CREATE TABLE notification_delivery (
    id UUID NOT NULL,
    notification_id UUID NOT NULL,
    notification_channel_id UUID NOT NULL,
    status VARCHAR NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notification_delivery PRIMARY KEY (id),
    CONSTRAINT uq_notification_delivery_notification_channel UNIQUE (notification_id, notification_channel_id),
    CONSTRAINT fk_notification_delivery_notification FOREIGN KEY (notification_id)
        REFERENCES notification (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_delivery_channel FOREIGN KEY (notification_channel_id)
        REFERENCES notification_channel (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_notification_delivery_notification_id ON notification_delivery (notification_id);
CREATE INDEX idx_notification_delivery_status_next_attempt ON notification_delivery (status, next_attempt_at);

--changeset blackoutradar:021-create-delivery-attempt
CREATE TABLE delivery_attempt (
    id UUID NOT NULL,
    notification_delivery_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    result VARCHAR,
    error_code VARCHAR,
    CONSTRAINT pk_delivery_attempt PRIMARY KEY (id),
    CONSTRAINT uq_delivery_attempt_delivery_number UNIQUE (notification_delivery_id, attempt_number),
    CONSTRAINT fk_delivery_attempt_delivery FOREIGN KEY (notification_delivery_id)
        REFERENCES notification_delivery (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);
