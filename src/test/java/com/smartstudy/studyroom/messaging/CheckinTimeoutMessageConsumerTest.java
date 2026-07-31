package com.smartstudy.studyroom.messaging;

import com.smartstudy.studyroom.service.ReservationTimeoutService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CheckinTimeoutMessageConsumerTest {

    @Test
    void shouldDelegateMessageToTimeoutService() {
        ReservationTimeoutService reservationTimeoutService =
                mock(ReservationTimeoutService.class);
        CheckinTimeoutMessageConsumer consumer =
                new CheckinTimeoutMessageConsumer(
                        reservationTimeoutService
                );
        LocalDateTime deadlineAt = LocalDateTime.of(
                2026,
                7,
                31,
                8,
                15
        );

        consumer.consume(new CheckinTimeoutMessage(1001L, deadlineAt));

        verify(reservationTimeoutService)
                .handleCheckinTimeoutMessage(1001L, deadlineAt);
    }
}
