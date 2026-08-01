package com.smartstudy.studyroom;

import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.mapper.ReservationTimeoutMessageMapper;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationTimeoutMessageOutboxServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T00:00:00Z"),
            ZoneId.systemDefault()
    );

    @Test
    void shouldCreatePendingTimeoutMessage() {
        ReservationTimeoutMessageMapper mapper =
                mock(ReservationTimeoutMessageMapper.class);
        ReservationTimeoutMessageService service =
                new ReservationTimeoutMessageService(mapper, CLOCK);
        LocalDateTime deadlineAt =
                LocalDateTime.of(2026, 8, 1, 8, 15);

        ReservationTimeoutMessage message =
                service.createPending(1001L, deadlineAt);

        assertThat(message.getReservationId()).isEqualTo(1001L);
        assertThat(message.getDeadlineAt()).isEqualTo(deadlineAt);
        assertThat(message.getStatus())
                .isEqualTo(ReservationTimeoutMessageService.STATUS_PENDING);
        assertThat(message.getNextRetryTime())
                .isEqualTo(LocalDateTime.now(CLOCK));

        verify(mapper).insert(message);
    }

    @Test
    void shouldMarkMessageSent() {
        ReservationTimeoutMessageMapper mapper =
                mock(ReservationTimeoutMessageMapper.class);
        ReservationTimeoutMessageService service =
                new ReservationTimeoutMessageService(mapper, CLOCK);

        service.markSent(2001L);

        verify(mapper).markSent(
                2001L,
                ReservationTimeoutMessageService.STATUS_PENDING,
                ReservationTimeoutMessageService.STATUS_FAILED,
                ReservationTimeoutMessageService.STATUS_SENT
        );
    }

    @Test
    void shouldMarkMessageFailedWithTruncatedErrorAndNextRetryTime() {
        ReservationTimeoutMessageMapper mapper =
                mock(ReservationTimeoutMessageMapper.class);
        ReservationTimeoutMessageService service =
                new ReservationTimeoutMessageService(mapper, CLOCK);
        String longError = "x".repeat(600);

        service.markFailed(2001L, longError);

        verify(mapper).markFailed(
                2001L,
                ReservationTimeoutMessageService.STATUS_FAILED,
                ReservationTimeoutMessageService.STATUS_SENT,
                LocalDateTime.now(CLOCK).plusMinutes(1),
                "x".repeat(500)
        );
    }

    @Test
    void shouldFindRetryableMessages() {
        ReservationTimeoutMessageMapper mapper =
                mock(ReservationTimeoutMessageMapper.class);
        ReservationTimeoutMessageService service =
                new ReservationTimeoutMessageService(mapper, CLOCK);
        ReservationTimeoutMessage message =
                new ReservationTimeoutMessage();

        when(mapper.findRetryable(
                ReservationTimeoutMessageService.STATUS_PENDING,
                ReservationTimeoutMessageService.STATUS_FAILED,
                5,
                LocalDateTime.now(CLOCK),
                50
        )).thenReturn(List.of(message));

        List<ReservationTimeoutMessage> messages =
                service.findRetryable(5, 50);

        assertThat(messages).containsExactly(message);
    }
}