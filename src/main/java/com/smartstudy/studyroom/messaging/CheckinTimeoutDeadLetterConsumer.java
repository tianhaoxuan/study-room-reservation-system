package com.smartstudy.studyroom.messaging;

import com.rabbitmq.client.Channel;
import com.smartstudy.studyroom.config.RabbitMqConfig;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class CheckinTimeoutDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(
            CheckinTimeoutDeadLetterConsumer.class
    );

    private final ReservationTimeoutMessageService messageService;

    public CheckinTimeoutDeadLetterConsumer(
            ReservationTimeoutMessageService messageService) {

        this.messageService = messageService;
    }

    @RabbitListener(
            id = RabbitMqConfig.CHECKIN_TIMEOUT_DEAD_LETTER_LISTENER_ID,
            queues = RabbitMqConfig.CHECKIN_TIMEOUT_FAILURE_QUEUE,
            autoStartup = "${studyroom.rabbitmq.checkin-timeout.dead-letter-listener-auto-startup:false}"
    )
    public void consume(
            CheckinTimeoutMessage message,
            Message rawMessage,
            Channel channel) throws IOException {

        long deliveryTag =
                rawMessage.getMessageProperties().getDeliveryTag();

        try {
            String reason = deadLetterReason(rawMessage);
            messageService.markDeadLetter(message.messageId(), reason);
            log.warn(
                    "Recorded check-in timeout dead letter, messageId={}, reservationId={}, reason={}",
                    message.messageId(),
                    message.reservationId(),
                    reason
            );
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            channel.basicNack(deliveryTag, false, true);
            throw e;
        }
    }

    private String deadLetterReason(Message rawMessage) {
        Map<String, Object> headers =
                rawMessage.getMessageProperties().getHeaders();

        Object reason = headers.get("x-first-death-reason");
        Object exchange = headers.get("x-first-death-exchange");
        Object queue = headers.get("x-first-death-queue");

        return "dead-letter: reason=" + valueOrUnknown(reason) +
                ", exchange=" + valueOrUnknown(exchange) +
                ", queue=" + valueOrUnknown(queue);
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }
}