package com.smartstudy.studyroom.messaging;

import com.smartstudy.studyroom.config.RabbitMqConfig;
import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class CheckinTimeoutMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(
            CheckinTimeoutMessagePublisher.class
    );

    private final RabbitTemplate rabbitTemplate;
    private final ReservationTimeoutMessageService
            reservationTimeoutMessageService;
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public CheckinTimeoutMessagePublisher(
            RabbitTemplate rabbitTemplate,
            ReservationTimeoutMessageService reservationTimeoutMessageService,
            @Value("${studyroom.rabbitmq.checkin-timeout.enabled:false}")
            boolean enabled) {

        this(
                rabbitTemplate,
                reservationTimeoutMessageService,
                enabled,
                Clock.systemDefaultZone()
        );
    }

    CheckinTimeoutMessagePublisher(
            RabbitTemplate rabbitTemplate,
            ReservationTimeoutMessageService reservationTimeoutMessageService,
            boolean enabled,
            Clock clock) {

        this.rabbitTemplate = rabbitTemplate;
        this.reservationTimeoutMessageService =
                reservationTimeoutMessageService;
        this.enabled = enabled;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(CheckinTimeoutScheduledEvent event) {
        if (!enabled) {
            return;
        }

        publishMessage(
                event.messageId(),
                event.reservationId(),
                event.deadlineAt()
        );
    }

    public void publishOutboxMessage(
            ReservationTimeoutMessage timeoutMessage) {

        if (!enabled) {
            return;
        }

        publishMessage(
                timeoutMessage.getId(),
                timeoutMessage.getReservationId(),
                timeoutMessage.getDeadlineAt()
        );
    }

    private void publishMessage(
            Long messageId,
            Long reservationId,
            LocalDateTime deadlineAt) {

        CheckinTimeoutMessage message = new CheckinTimeoutMessage(
                messageId,
                reservationId,
                deadlineAt
        );

        long delayMillis = Math.max(
                0,
                Duration.between(
                        LocalDateTime.now(clock),
                        deadlineAt
                ).toMillis()
        );

        String correlationId = correlationId(
                messageId,
                reservationId,
                deadlineAt
        );
        CorrelationData correlationData = new CorrelationData(correlationId);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_EXCHANGE,
                    RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_ROUTING_KEY,
                    message,
                    rabbitMessage -> {
                        rabbitMessage.getMessageProperties()
                                .setExpiration(String.valueOf(delayMillis));
                        rabbitMessage.getMessageProperties()
                                .setMessageId(correlationId);
                        return rabbitMessage;
                    },
                    correlationData
            );
        } catch (AmqpException e) {
            reservationTimeoutMessageService.markFailed(
                    messageId,
                    e.getMessage()
            );
            log.warn(
                    "Failed to publish check-in timeout message, " +
                            "messageId={}, reservationId={}, " +
                            "correlationId={}, reason={}",
                    messageId,
                    reservationId,
                    correlationId,
                    e.getMessage()
            );
        }
    }

    private String correlationId(
            Long messageId,
            Long reservationId,
            LocalDateTime deadlineAt) {

        return "checkin-timeout:"
                + messageId
                + ":"
                + reservationId
                + ":"
                + deadlineAt;
    }
}