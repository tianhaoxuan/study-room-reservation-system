CREATE TABLE reservation_slot_occupancy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'occupancy id',
    reservation_id BIGINT NOT NULL COMMENT 'reservation id',
    user_id BIGINT NOT NULL COMMENT 'user id',
    seat_id BIGINT NOT NULL COMMENT 'seat id',
    room_id BIGINT NOT NULL COMMENT 'room id',
    reservation_date DATE NOT NULL COMMENT 'reservation date',
    slot_id BIGINT NOT NULL COMMENT 'atomic reservation slot id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',

    UNIQUE KEY uk_occupancy_seat_date_slot (
        seat_id,
        reservation_date,
        slot_id
    ),

    UNIQUE KEY uk_occupancy_user_date_slot (
        user_id,
        reservation_date,
        slot_id
    ),

    KEY idx_occupancy_reservation_id (reservation_id),
    KEY idx_occupancy_room_date_slot (
        room_id,
        reservation_date,
        slot_id
    )
) COMMENT = 'active atomic slot occupancy table';