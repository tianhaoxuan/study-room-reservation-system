ALTER TABLE reservation_timeout_message
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1
        COMMENT '1 pending, 2 sent, 3 failed, 4 consumed, 5 dead letter';

ALTER TABLE reservation_timeout_message
    ADD COLUMN dead_letter_time DATETIME COMMENT 'dead letter handled time'
        AFTER consumed_time;

CREATE INDEX idx_timeout_message_dead_letter_time
    ON reservation_timeout_message(status, dead_letter_time);