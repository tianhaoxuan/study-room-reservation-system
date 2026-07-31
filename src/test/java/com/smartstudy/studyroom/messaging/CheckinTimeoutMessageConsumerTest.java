package com.smartstudy.studyroom.messaging;

import com.rabbitmq.client.Channel;
import com.smartstudy.studyroom.service.ReservationTimeoutService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CheckinTimeoutMessageConsumerTest {

    @Test
    void shouldDelegateMessageToTimeoutServiceAndAck() throws IOException {
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
        Message rawMessage = rawMessage(99L);
        Channel channel = mock(Channel.class);

        consumer.consume(
                new CheckinTimeoutMessage(1001L, deadlineAt),
                rawMessage,
                channel
        );

        verify(reservationTimeoutService)
                .handleCheckinTimeoutMessage(1001L, deadlineAt);
        verify(channel).basicAck(99L, false);
    }

    @Test
    void shouldRejectWithoutRequeueWhenTimeoutHandlingFails()
            throws IOException {

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
        RuntimeException failure = new RuntimeException("db error");
        doThrow(failure)
                .when(reservationTimeoutService)
                .handleCheckinTimeoutMessage(1001L, deadlineAt);

        Message rawMessage = rawMessage(99L);
        Channel channel = mock(Channel.class);

        assertThatThrownBy(() -> consumer.consume(
                new CheckinTimeoutMessage(1001L, deadlineAt),
                rawMessage,
                channel
        )).isSameAs(failure);

        verify(channel).basicReject(99L, false);
    }

    private Message rawMessage(long deliveryTag) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], messageProperties);
    }
}