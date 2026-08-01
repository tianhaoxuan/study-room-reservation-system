package com.smartstudy.studyroom.messaging;

import com.smartstudy.studyroom.config.RabbitMqConfig;
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

        CheckinTimeoutMessage message = new CheckinTimeoutMessage(
                event.reservationId(),
                event.deadlineAt()
        );

        long delayMillis = Math.max(
                0,
                Duration.between(
                        LocalDateTime.now(clock),
                        event.deadlineAt()
                ).toMillis()
        );

        String correlationId = correlationId(event);
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
                    event.messageId(),
                    e.getMessage()
            );
            log.warn(
                    "Failed to publish check-in timeout message, " +
                            "messageId={}, reservationId={}, " +
                            "correlationId={}, reason={}",
                    event.messageId(),
                    event.reservationId(),
                    correlationId,
                    e.getMessage()
            );
        }
    }

    private String correlationId(CheckinTimeoutScheduledEvent event) {
        return "checkin-timeout:"
                + event.messageId()
                + ":"
                + event.reservationId()
                + ":"
                + event.deadlineAt();
    }
}