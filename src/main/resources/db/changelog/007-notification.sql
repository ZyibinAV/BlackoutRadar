--liquibase formatted sql

--changeset blackoutradar:015-create-notification
CREATE TABLE notification (
    id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    power_outage_id UUID NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT uq_notification_subscription_outage UNIQUE (subscription_id, power_outage_id),
    CONSTRAINT fk_notification_subscription FOREIGN KEY (subscription_id)
        REFERENCES subscription (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_power_outage FOREIGN KEY (power_outage_id)
        REFERENCES power_outage (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_notification_subscription_id ON notification (subscription_id);
CREATE INDEX idx_notification_power_outage_id ON notification (power_outage_id);
CREATE INDEX idx_notification_status ON notification (status);
