CREATE DATABASE IF NOT EXISTS zixishi
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE zixishi;

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'user id',
    openid VARCHAR(100) UNIQUE COMMENT 'wechat openid',
    student_no VARCHAR(50) UNIQUE COMMENT 'student number',
    real_name VARCHAR(50) COMMENT 'real name',
    nickname VARCHAR(100) COMMENT 'nickname',
    avatar_url VARCHAR(255) COMMENT 'avatar url',
    credit_score INT DEFAULT 100 COMMENT 'credit score',
    violation_count INT DEFAULT 0 COMMENT 'violation count',
    status TINYINT DEFAULT 1 COMMENT '1 normal, 0 disabled',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time'
) COMMENT='user table';

CREATE TABLE IF NOT EXISTS building (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'building id',
    building_name VARCHAR(100) NOT NULL COMMENT 'building name',
    status TINYINT DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    UNIQUE KEY uk_building_name (building_name)
) COMMENT='building table';

CREATE TABLE IF NOT EXISTS study_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'study room id',
    building_id BIGINT NOT NULL COMMENT 'building id',
    room_name VARCHAR(100) NOT NULL COMMENT 'room name',
    total_seats INT DEFAULT 0 COMMENT 'total seats',
    reserved_seats INT DEFAULT 0 COMMENT 'reserved seats',
    occupancy_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT 'occupancy rate',
    open_time TIME NOT NULL COMMENT 'open time',
    close_time TIME NOT NULL COMMENT 'close time',
    status TINYINT DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    INDEX idx_building_id (building_id),
    UNIQUE KEY uk_building_room (building_id, room_name)
) COMMENT='study room table';

CREATE TABLE IF NOT EXISTS seat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'seat id',
    room_id BIGINT NOT NULL COMMENT 'room id',
    seat_no VARCHAR(30) NOT NULL COMMENT 'seat number',
    x INT NOT NULL COMMENT 'x coordinate',
    y INT NOT NULL COMMENT 'y coordinate',
    has_power TINYINT DEFAULT 0 COMMENT '1 yes, 0 no',
    near_window TINYINT DEFAULT 0 COMMENT '1 yes, 0 no',
    status TINYINT DEFAULT 1 COMMENT '1 free, 2 reserved, 3 in use, 4 repair',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    INDEX idx_room_id (room_id),
    UNIQUE KEY uk_room_seat (room_id, seat_no)
) COMMENT='seat table';

CREATE TABLE IF NOT EXISTS reservation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'reservation id',
    user_id BIGINT NOT NULL COMMENT 'user id',
    seat_id BIGINT NOT NULL COMMENT 'seat id',
    room_id BIGINT NOT NULL COMMENT 'room id',
    reservation_date DATE NOT NULL COMMENT 'reservation date',
    time_slot VARCHAR(30) NOT NULL COMMENT 'time slot',
    start_time TIME NOT NULL COMMENT 'start time',
    end_time TIME NOT NULL COMMENT 'end time',
    status TINYINT DEFAULT 1 COMMENT '1 pending, 2 in use, 3 finished, 4 canceled, 5 violated',
    sign_time DATETIME COMMENT 'sign time',
    leave_time DATETIME COMMENT 'leave time',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    INDEX idx_user_date (user_id, reservation_date),
    INDEX idx_seat_slot (seat_id, reservation_date, time_slot),
    INDEX idx_room_date (room_id, reservation_date)
) COMMENT='reservation table';

CREATE TABLE IF NOT EXISTS violation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'violation id',
    user_id BIGINT NOT NULL COMMENT 'user id',
    reservation_id BIGINT NOT NULL COMMENT 'reservation id',
    violation_type TINYINT NOT NULL COMMENT '1 no show, 2 check-in timeout',
    reason VARCHAR(255) COMMENT 'reason',
    handle_result VARCHAR(255) COMMENT 'handle result',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    INDEX idx_user_id (user_id)
) COMMENT='violation table';

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'config id',
    config_key VARCHAR(100) UNIQUE COMMENT 'config key',
    config_value VARCHAR(100) COMMENT 'config value',
    description VARCHAR(255) COMMENT 'description',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time'
) COMMENT='system config table';

CREATE TABLE IF NOT EXISTS notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'notice id',
    title VARCHAR(200) NOT NULL COMMENT 'title',
    content TEXT COMMENT 'content',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time'
) COMMENT='notice table';
