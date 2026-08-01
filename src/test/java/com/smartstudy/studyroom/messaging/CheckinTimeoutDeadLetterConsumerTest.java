package com.smartstudy.studyroom.messaging;

import com.rabbitmq.client.Channel;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CheckinTimeoutDeadLetterConsumerTest {

    @Test
    void shouldMarkMessageDeadLetterAndAck() throws IOException {
        ReservationTimeoutMessageService messageService =
                mock(ReservationTimeoutMessageService.class);
        CheckinTimeoutDeadLetterConsumer consumer =
                new CheckinTimeoutDeadLetterConsumer(messageService);

        Message rawMessage = rawMessage(99L);
        rawMessage.getMessageProperties().setHeader(
                "x-first-death-reason",
                "rejected"
        );
        rawMessage.getMessageProperties().setHeader(
                "x-first-death-exchange",
                "studyroom.reservation.checkin.event.exchange"
        );
        rawMessage.getMessageProperties().setHeader(
                "x-first-death-queue",
                "studyroom.reservation.checkin.timeout.queue"
        );

        Channel channel = mock(Channel.class);
        LocalDateTime deadlineAt =
                LocalDateTime.of(2026, 8, 1, 8, 15);

        consumer.consume(
                new CheckinTimeoutMessage(2001L, 1001L, deadlineAt),
                rawMessage,
                channel
        );

        verify(messageService).markDeadLetter(
                2001L,
                "dead-letter: reason=rejected, " +
                        "exchange=studyroom.reservation.checkin.event.exchange, " +
                        "queue=studyroom.reservation.checkin.timeout.queue"
        );
        verify(channel).basicAck(99L, false);
    }

    @Test
    void shouldRequeueWhenDeadLetterRecordingFails() throws IOException {
        ReservationTimeoutMessageService messageService =
                mock(ReservationTimeoutMessageService.class);
        CheckinTimeoutDeadLetterConsumer consumer =
                new CheckinTimeoutDeadLetterConsumer(messageService);
        RuntimeException failure = new RuntimeException("db down");

        doThrow(failure).when(messageService).markDeadLetter(
                org.mockito.ArgumentMatchers.eq(2001L),
                org.mockito.ArgumentMatchers.any(String.class)
        );

        Message rawMessage = rawMessage(99L);
        Channel channel = mock(Channel.class);
        LocalDateTime deadlineAt =
                LocalDateTime.of(2026, 8, 1, 8, 15);

        assertThatThrownBy(() -> consumer.consume(
                new CheckinTimeoutMessage(2001L, 1001L, deadlineAt),
                rawMessage,
                channel
        )).isSameAs(failure);

        verify(channel).basicNack(99L, false, true);
    }

    private Message rawMessage(long deliveryTag) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], messageProperties);
    }
}