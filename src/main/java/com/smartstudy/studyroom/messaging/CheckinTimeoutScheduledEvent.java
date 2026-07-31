package com.smartstudy.studyroom.messaging;

import java.time.LocalDateTime;

public record CheckinTimeoutScheduledEvent(
        Long reservationId,
        LocalDateTime deadlineAt) {
}
