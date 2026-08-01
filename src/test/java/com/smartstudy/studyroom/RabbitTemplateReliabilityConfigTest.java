package com.smartstudy.studyroom;

import com.smartstudy.studyroom.config.RabbitTemplateReliabilityConfig;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RabbitTemplateReliabilityConfigTest {

    @Test
    void shouldMarkMessageSentWhenBrokerConfirmsAck() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ReservationTimeoutMessageService messageService =
                mock(ReservationTimeoutMessageService.class);
        RabbitTemplate.ConfirmCallback confirmCallback =
                configuredConfirmCallback(rabbitTemplate, messageService);

        confirmCallback.confirm(
                new CorrelationData(
                        "checkin-timeout:2001:1001:2026-08-01T08:15"
                ),
                true,
                null
        );

        verify(messageService).markSent(2001L);
        verify(messageService, never()).markFailed(
                org.mockito.ArgumentMatchers.any(Long.class),
                org.mockito.ArgumentMatchers.any(String.class)
        );
    }

    @Test
    void shouldMarkMessageFailedWhenBrokerConfirmsNack() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ReservationTimeoutMessageService messageService =
                mock(ReservationTimeoutMessageService.class);
        RabbitTemplate.ConfirmCallback confirmCallback =
                configuredConfirmCallback(rabbitTemplate, messageService);

        confirmCallback.confirm(
                new CorrelationData(
                        "checkin-timeout:2002:1001:2026-08-01T08:15"
                ),
                false,
                "exchange missing"
        );

        verify(messageService).markFailed(2002L, "exchange missing");
        verify(messageService, never()).markSent(2002L);
    }

    @Test
    void shouldIgnoreConfirmWhenCorrelationIdIsInvalid() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ReservationTimeoutMessageService messageService =
                mock(ReservationTimeoutMessageService.class);
        RabbitTemplate.ConfirmCallback confirmCallback =
                configuredConfirmCallback(rabbitTemplate, messageService);

        confirmCallback.confirm(
                new CorrelationData("invalid"),
                true,
                null
        );

        verify(messageService, never()).markSent(
                org.mockito.ArgumentMatchers.any(Long.class)
        );
        verify(messageService, never()).markFailed(
                org.mockito.ArgumentMatchers.any(Long.class),
                org.mockito.ArgumentMatchers.any(String.class)
        );
    }

    @Test
    void shouldMarkMessageFailedWhenMessageIsReturned() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ReservationTimeoutMessageService messageService =
                mock(ReservationTimeoutMessageService.class);
        RabbitTemplate.ReturnsCallback returnsCallback =
                configuredReturnsCallback(rabbitTemplate, messageService);

        MessageProperties properties = new MessageProperties();
        properties.setMessageId(
                "checkin-timeout:2003:1001:2026-08-01T08:15"
        );

        ReturnedMessage returnedMessage = new ReturnedMessage(
                new Message(new byte[0], properties),
                312,
                "NO_ROUTE",
                "studyroom.reservation.checkin.delay.exchange",
                "reservation.checkin.timeout.missing"
        );

        returnsCallback.returnedMessage(returnedMessage);

        verify(messageService).markFailed(
                2003L,
                "returned: replyCode=312, replyText=NO_ROUTE, " +
                        "exchange=studyroom.reservation.checkin.delay.exchange, " +
                        "routingKey=reservation.checkin.timeout.missing"
        );
        verify(messageService, never()).markSent(2003L);
    }

    @Test
    void shouldConfigureRabbitTemplateReliabilityCallbacks() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ReservationTimeoutMessageService messageService =
                mock(ReservationTimeoutMessageService.class);
        RabbitTemplateReliabilityConfig config =
                new RabbitTemplateReliabilityConfig(
                        rabbitTemplate,
                        messageService
                );

        config.configureRabbitTemplateCallbacks();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RabbitTemplate.ReturnsCallback> returnsCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ReturnsCallback.class);

        verify(rabbitTemplate).setMandatory(true);
        verify(rabbitTemplate).setConfirmCallback(confirmCaptor.capture());
        verify(rabbitTemplate).setReturnsCallback(returnsCaptor.capture());

        org.assertj.core.api.Assertions.assertThat(confirmCaptor.getValue())
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(returnsCaptor.getValue())
                .isNotNull();
    }

    private RabbitTemplate.ConfirmCallback configuredConfirmCallback(
            RabbitTemplate rabbitTemplate,
            ReservationTimeoutMessageService messageService) {

        RabbitTemplateReliabilityConfig config =
                new RabbitTemplateReliabilityConfig(
                        rabbitTemplate,
                        messageService
                );
        config.configureRabbitTemplateCallbacks();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        verify(rabbitTemplate).setConfirmCallback(confirmCaptor.capture());

        return confirmCaptor.getValue();
    }

    private RabbitTemplate.ReturnsCallback configuredReturnsCallback(
            RabbitTemplate rabbitTemplate,
            ReservationTimeoutMessageService messageService) {

        RabbitTemplateReliabilityConfig config =
                new RabbitTemplateReliabilityConfig(
                        rabbitTemplate,
                        messageService
                );
        config.configureRabbitTemplateCallbacks();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RabbitTemplate.ReturnsCallback> returnsCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ReturnsCallback.class);
        verify(rabbitTemplate).setReturnsCallback(returnsCaptor.capture());

        return returnsCaptor.getValue();
    }
}