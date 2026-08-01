CREATE TABLE IF NOT EXISTS reservation_timeout_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'message id',
    reservation_id BIGINT NOT NULL COMMENT 'reservation id',
    deadline_at DATETIME NOT NULL COMMENT 'check-in timeout deadline',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 pending, 2 sent, 3 failed',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'retry count',
    last_error VARCHAR(500) COMMENT 'last publish error',
    sent_time DATETIME COMMENT 'sent time',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    UNIQUE KEY uk_reservation_timeout_message_reservation (reservation_id),
    INDEX idx_status_create_time (status, create_time),
    INDEX idx_deadline_at (deadline_at)
) COMMENT='reservation timeout message outbox';