package com.smartstudy.studyroom.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class SeatOccupancyBitmapKey {

    private final String keyPrefix;

    public SeatOccupancyBitmapKey(
            @Value("${studyroom.redis.seat-occupancy.key-prefix:seat:bitmap}")
            String keyPrefix) {

        this.keyPrefix = normalizePrefix(keyPrefix);
    }

    public String forSlot(
            Long roomId,
            LocalDate reservationDate,
            Long slotId) {

        if (roomId == null) {
            throw new IllegalArgumentException("roomId must not be null");
        }
        if (reservationDate == null) {
            throw new IllegalArgumentException(
                    "reservationDate must not be null"
            );
        }
        if (slotId == null) {
            throw new IllegalArgumentException("slotId must not be null");
        }

        return keyPrefix + ":"
                + roomId + ":"
                + reservationDate + ":"
                + slotId;
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "seat:bitmap";
        }
        return value.strip();
    }
}