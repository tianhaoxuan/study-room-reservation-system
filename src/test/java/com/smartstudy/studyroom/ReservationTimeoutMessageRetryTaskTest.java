package com.smartstudy.studyroom;

import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.messaging.CheckinTimeoutMessagePublisher;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import com.smartstudy.studyroom.task.ReservationTimeoutMessageRetryTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationTimeoutMessageRetryTaskTest {

    @Test
    void shouldPublishRetryableTimeoutMessages() {
        ReservationTimeoutMessageService messageService =
                mock(ReservationTimeoutMessageService.class);
        CheckinTimeoutMessagePublisher publisher =
                mock(CheckinTimeoutMessagePublisher.class);
        ReservationTimeoutMessageRetryTask task =
                new ReservationTimeoutMessageRetryTask(
                        messageService,
                        publisher,
                        5,
                        50
                );

        ReservationTimeoutMessage first =
                new ReservationTimeoutMessage();
        first.setId(2001L);
        ReservationTimeoutMessage second =
                new ReservationTimeoutMessage();
        second.setId(2002L);

        when(messageService.findRetryable(5, 50))
                .thenReturn(List.of(first, second));

        task.retryTimeoutMessages();

        verify(publisher).publishOutboxMessage(first);
        verify(publisher).publishOutboxMessage(second);
    }
}