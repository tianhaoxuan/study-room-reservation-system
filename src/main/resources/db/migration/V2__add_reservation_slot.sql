CREATE TABLE reservation_slot (
                                  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'atomic slot id',
                                  slot_code VARCHAR(20) NOT NULL COMMENT 'stable slot code',
                                  slot_name VARCHAR(30) NOT NULL COMMENT 'display name',
                                  start_time TIME NOT NULL COMMENT 'slot start time',
                                  end_time TIME NOT NULL COMMENT 'slot end time',
                                  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 enabled',
                                  display_order INT NOT NULL COMMENT 'display order',
                                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  UNIQUE KEY uk_reservation_slot_code (slot_code),
                                  UNIQUE KEY uk_reservation_slot_time (start_time, end_time),
                                  CONSTRAINT chk_reservation_slot_time CHECK (end_time > start_time)
) COMMENT = '30-minute atomic reservation slots';


INSERT INTO reservation_slot (
    slot_code,
    slot_name,
    start_time,
    end_time,
    enabled,
    display_order
)
WITH RECURSIVE slot_series AS (
    SELECT
        CAST('07:30:00' AS TIME) AS start_time,
        1 AS display_order

    UNION ALL

    SELECT
        ADDTIME(start_time, '00:30:00'),
        display_order + 1
    FROM slot_series
    WHERE start_time < CAST('22:30:00' AS TIME)
)
SELECT
    CONCAT('S', LPAD(display_order, 3, '0')),
    CONCAT(
            TIME_FORMAT(start_time, '%H:%i'),
            '-',
            TIME_FORMAT(ADDTIME(start_time, '00:30:00'), '%H:%i')
        ),
    start_time,
    ADDTIME(start_time, '00:30:00'),
    1,
    display_order
FROM slot_series;