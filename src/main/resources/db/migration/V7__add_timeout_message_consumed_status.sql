ALTER TABLE reservation_timeout_message
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1
        COMMENT '1 pending, 2 sent, 3 failed, 4 consumed';

ALTER TABLE reservation_timeout_message
    ADD COLUMN consumed_time DATETIME COMMENT 'consumer handled time'
        AFTER sent_time;

CREATE INDEX idx_timeout_message_consumed_time
    ON reservation_timeout_message(status, consumed_time);