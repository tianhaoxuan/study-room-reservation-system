package com.smartstudy.studyroom.redis;

public enum SeatPreOccupyStatus {

    PREOCCUPIED,
    IDEMPOTENT_PREOCCUPIED,
    USER_CONFLICT,
    SEAT_CONFLICT,
    REQUEST_CONFLICT,
    RELEASED,
    IDEMPOTENT_RELEASED,
    DISABLED,
    INVALID,
    FAILED
}