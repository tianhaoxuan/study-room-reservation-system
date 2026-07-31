package com.smartstudy.studyroom.messaging;

import java.time.LocalDateTime;

public record CheckinTimeoutMessage(
        Long reservationId,
        LocalDateTime deadlineAt) {
}
