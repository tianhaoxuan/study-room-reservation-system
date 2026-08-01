ALTER TABLE reservation
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT 'client idempotency request id'
        AFTER id;

CREATE UNIQUE INDEX uk_reservation_user_request
    ON reservation(user_id, request_id);