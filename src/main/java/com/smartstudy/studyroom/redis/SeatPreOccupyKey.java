package com.smartstudy.studyroom.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class SeatPreOccupyKey {

    private final String keyPrefix;

    public SeatPreOccupyKey(
            @Value("${studyroom.redis.seat-preoccupy.key-prefix:seat:preoccupy}")
            String keyPrefix) {

        this.keyPrefix = normalizePrefix(keyPrefix);
    }

    public String forRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException(
                    "requestId must not be blank"
            );
        }
        return keyPrefix + ":request:" + requestId.strip();
    }

    public String requestPattern() {
        return keyPrefix + ":request:*";
    }

    public String forUser(Long userId, LocalDate reservationDate) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (reservationDate == null) {
            throw new IllegalArgumentException(
                    "reservationDate must not be null"
            );
        }
        return keyPrefix + ":user:" + userId + ":" + reservationDate;
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "seat:preoccupy";
        }
        return value.strip();
    }
}