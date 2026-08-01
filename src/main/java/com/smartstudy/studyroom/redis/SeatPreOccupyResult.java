package com.smartstudy.studyroom.redis;

public record SeatPreOccupyResult(
        SeatPreOccupyStatus status,
        String message) {

    public static SeatPreOccupyResult of(
            SeatPreOccupyStatus status,
            String message) {

        return new SeatPreOccupyResult(status, message);
    }

    public boolean success() {
        return status == SeatPreOccupyStatus.PREOCCUPIED
                || status == SeatPreOccupyStatus.IDEMPOTENT_PREOCCUPIED
                || status == SeatPreOccupyStatus.RELEASED
                || status == SeatPreOccupyStatus.IDEMPOTENT_RELEASED;
    }
}