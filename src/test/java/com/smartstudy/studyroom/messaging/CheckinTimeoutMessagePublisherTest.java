package com.smartstudy.studyroom.messaging;

import com.smartstudy.studyroom.config.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CheckinTimeoutMessagePublisherTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-31T00:00:00Z"),
            ZoneId.systemDefault()
    );

    @Test
    void shouldPublishDelayedMessageAfterTransactionCommitEvent()
            throws Exception {

        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        CheckinTimeoutMessagePublisher publisher =
                new CheckinTimeoutMessagePublisher(
                        rabbitTemplate,
                        true,
                        CLOCK
                );
        LocalDateTime deadlineAt = LocalDateTime.now(CLOCK).plusMinutes(15);

        publisher.publish(new CheckinTimeoutScheduledEvent(1001L, deadlineAt));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MessagePostProcessor> processorCaptor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationCaptor =
                ArgumentCaptor.forClass(CorrelationData.class);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_EXCHANGE),
                eq(RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_ROUTING_KEY),
                eq(new CheckinTimeoutMessage(1001L, deadlineAt)),
                processorCaptor.capture(),
                correlationCaptor.capture()
        );

        MessageProperties properties = new MessageProperties();
        Message processed = processorCaptor.getValue()
                .postProcessMessage(new Message(new byte[0], properties));

        String expectedCorrelationId =
                "checkin-timeout:1001:" + deadlineAt;

        assertThat(processed.getMessageProperties().getExpiration())
                .isEqualTo("900000");
        assertThat(processed.getMessageProperties().getMessageId())
                .isEqualTo(expectedCorrelationId);
        assertThat(correlationCaptor.getValue().getId())
                .isEqualTo(expectedCorrelationId);
    }

    @Test
    void shouldNotPublishWhenDisabled() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        CheckinTimeoutMessagePublisher publisher =
                new CheckinTimeoutMessagePublisher(
                        rabbitTemplate,
                        false,
                        CLOCK
                );

        publisher.publish(new CheckinTimeoutScheduledEvent(
                1001L,
                LocalDateTime.now(CLOCK).plusMinutes(15)
        ));

        verify(rabbitTemplate, never()).convertAndSend(
                any(String.class),
                any(String.class),
                any(Object.class),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
    }

    @Test
    void shouldNotThrowWhenRabbitMqPublishFails() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doThrow(new AmqpException("connection failed"))
                .when(rabbitTemplate)
                .convertAndSend(
                        any(String.class),
                        any(String.class),
                        any(Object.class),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class)
                );
        CheckinTimeoutMessagePublisher publisher =
                new CheckinTimeoutMessagePublisher(
                        rabbitTemplate,
                        true,
                        CLOCK
                );

        publisher.publish(new CheckinTimeoutScheduledEvent(
                1001L,
                LocalDateTime.now(CLOCK).plusMinutes(15)
        ));
    }
}