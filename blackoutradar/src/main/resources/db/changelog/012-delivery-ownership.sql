--liquibase formatted sql

--changeset blackoutradar:022-add-delivery-processing-token
ALTER TABLE notification_delivery ADD COLUMN processing_token UUID;
