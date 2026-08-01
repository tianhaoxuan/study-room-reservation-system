package com.smartstudy.studyroom.messaging;

import java.time.LocalDateTime;

public record CheckinTimeoutScheduledEvent(
        Long messageId,
        Long reservationId,
        LocalDateTime deadlineAt) {
}