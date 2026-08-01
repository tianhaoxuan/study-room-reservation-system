ALTER TABLE reservation_timeout_message
    ADD COLUMN next_retry_time DATETIME COMMENT 'next retry time'
        AFTER retry_count;

CREATE INDEX idx_timeout_message_retry
    ON reservation_timeout_message(status, next_retry_time, retry_count);