package com.smartstudy.studyroom.config;

import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RabbitTemplateReliabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(
            RabbitTemplateReliabilityConfig.class
    );

    private static final String CHECKIN_TIMEOUT_PREFIX = "checkin-timeout:";

    private final RabbitTemplate rabbitTemplate;
    private final ReservationTimeoutMessageService
            reservationTimeoutMessageService;

    public RabbitTemplateReliabilityConfig(
            RabbitTemplate rabbitTemplate,
            ReservationTimeoutMessageService reservationTimeoutMessageService) {

        this.rabbitTemplate = rabbitTemplate;
        this.reservationTimeoutMessageService =
                reservationTimeoutMessageService;
    }

    @PostConstruct
    public void configureRabbitTemplateCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(this::handleConfirm);
        rabbitTemplate.setReturnsCallback(this::handleReturned);
    }

    private void handleConfirm(
            CorrelationData correlationData,
            boolean ack,
            String cause) {

        String correlationId = correlationData == null
                ? null
                : correlationData.getId();
        Long messageId = parseMessageId(correlationId);

        if (messageId == null) {
            log.warn(
                    "RabbitMQ confirm has invalid correlationId={}, ack={}, cause={}",
                    correlationId,
                    ack,
                    cause
            );
            return;
        }

        if (ack) {
            reservationTimeoutMessageService.markSent(messageId);
            log.debug(
                    "RabbitMQ message confirmed, messageId={}, correlationId={}",
                    messageId,
                    correlationId
            );
            return;
        }

        reservationTimeoutMessageService.markFailed(messageId, cause);
        log.warn(
                "RabbitMQ message was not confirmed, messageId={}, " +
                        "correlationId={}, cause={}",
                messageId,
                correlationId,
                cause
        );
    }

    private void handleReturned(ReturnedMessage returnedMessage) {
        String messageIdText = returnedMessage.getMessage()
                .getMessageProperties()
                .getMessageId();
        Long messageId = parseMessageId(messageIdText);

        String reason = "returned: replyCode="
                + returnedMessage.getReplyCode()
                + ", replyText="
                + returnedMessage.getReplyText()
                + ", exchange="
                + returnedMessage.getExchange()
                + ", routingKey="
                + returnedMessage.getRoutingKey();

        if (messageId == null) {
            log.warn(
                    "RabbitMQ message returned with invalid messageId={}, {}",
                    messageIdText,
                    reason
            );
            return;
        }

        reservationTimeoutMessageService.markFailed(messageId, reason);
        log.warn(
                "RabbitMQ message returned, messageId={}, exchange={}, " +
                        "routingKey={}, replyCode={}, replyText={}",
                messageId,
                returnedMessage.getExchange(),
                returnedMessage.getRoutingKey(),
                returnedMessage.getReplyCode(),
                returnedMessage.getReplyText()
        );
    }

    private Long parseMessageId(String correlationId) {
        if (correlationId == null
                || !correlationId.startsWith(CHECKIN_TIMEOUT_PREFIX)) {
            return null;
        }

        String payload = correlationId.substring(
                CHECKIN_TIMEOUT_PREFIX.length()
        );
        int separatorIndex = payload.indexOf(':');
        if (separatorIndex < 0) {
            return null;
        }

        try {
            return Long.valueOf(payload.substring(0, separatorIndex));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}