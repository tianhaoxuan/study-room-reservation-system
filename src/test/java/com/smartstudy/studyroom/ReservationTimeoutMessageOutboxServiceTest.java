package com.smartstudy.studyroom;

import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.mapper.ReservationTimeoutMessageMapper;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReservationTimeoutMessageOutboxServiceTest {

    @Test
    void shouldCreatePendingTimeoutMessage() {
        ReservationTimeoutMessageMapper mapper =
                mock(ReservationTimeoutMessageMapper.class);
        ReservationTimeoutMessageService service =
                new ReservationTimeoutMessageService(mapper);
        LocalDateTime deadlineAt =
                LocalDateTime.of(2026, 8, 1, 8, 15);

        ReservationTimeoutMessage message =
                service.createPending(1001L, deadlineAt);

        assertThat(message.getReservationId()).isEqualTo(1001L);
        assertThat(message.getDeadlineAt()).isEqualTo(deadlineAt);
        assertThat(message.getStatus())
                .isEqualTo(ReservationTimeoutMessageService.STATUS_PENDING);

        verify(mapper).insert(message);
    }

    @Test
    void shouldMarkMessageSent() {
        ReservationTimeoutMessageMapper mapper =
                mock(ReservationTimeoutMessageMapper.class);
        ReservationTimeoutMessageService service =
                new ReservationTimeoutMessageService(mapper);

        service.markSent(2001L);

        verify(mapper).markSent(
                2001L,
                ReservationTimeoutMessageService.STATUS_PENDING,
                ReservationTimeoutMessageService.STATUS_FAILED,
                ReservationTimeoutMessageService.STATUS_SENT
        );
    }

    @Test
    void shouldMarkMessageFailedWithTruncatedError() {
        ReservationTimeoutMessageMapper mapper =
                mock(ReservationTimeoutMessageMapper.class);
        ReservationTimeoutMessageService service =
                new ReservationTimeoutMessageService(mapper);
        String longError = "x".repeat(600);

        service.markFailed(2001L, longError);

        verify(mapper).markFailed(
                2001L,
                ReservationTimeoutMessageService.STATUS_FAILED,
                ReservationTimeoutMessageService.STATUS_SENT,
                "x".repeat(500)
        );
    }
}