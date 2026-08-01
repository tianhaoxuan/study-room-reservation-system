package com.smartstudy.studyroom.messaging;

import java.time.LocalDateTime;

public record CheckinTimeoutMessage(
        Long messageId,
        Long reservationId,
        LocalDateTime deadlineAt) {

    public CheckinTimeoutMessage(
            Long reservationId,
            LocalDateTime deadlineAt) {

        this(null, reservationId, deadlineAt);
    }
}