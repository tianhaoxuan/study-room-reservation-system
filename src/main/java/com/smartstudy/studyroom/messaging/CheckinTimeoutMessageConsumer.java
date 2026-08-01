package com.smartstudy.studyroom.messaging;

import com.rabbitmq.client.Channel;
import com.smartstudy.studyroom.config.RabbitMqConfig;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import com.smartstudy.studyroom.service.ReservationTimeoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CheckinTimeoutMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(
            CheckinTimeoutMessageConsumer.class
    );

    private final ReservationTimeoutService reservationTimeoutService;
    private final ReservationTimeoutMessageService
            reservationTimeoutMessageService;

    public CheckinTimeoutMessageConsumer(
            ReservationTimeoutService reservationTimeoutService,
            ReservationTimeoutMessageService reservationTimeoutMessageService) {

        this.reservationTimeoutService = reservationTimeoutService;
        this.reservationTimeoutMessageService =
                reservationTimeoutMessageService;
    }

    @RabbitListener(
            queues = RabbitMqConfig.CHECKIN_TIMEOUT_QUEUE,
            autoStartup = "${studyroom.rabbitmq.checkin-timeout.listener-auto-startup:false}"
    )
    public void consume(
            CheckinTimeoutMessage message,
            Message rawMessage,
            Channel channel) throws IOException {

        long deliveryTag =
                rawMessage.getMessageProperties().getDeliveryTag();

        try {
            boolean handled =
                    reservationTimeoutService.handleCheckinTimeoutMessage(
                            message.reservationId(),
                            message.deadlineAt()
                    );
            if (!handled) {
                log.debug(
                        "Ignored check-in timeout message, reservationId={}, messageId={}",
                        message.reservationId(),
                        message.messageId()
                );
            }

            reservationTimeoutMessageService.markConsumed(
                    message.messageId()
            );
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            channel.basicReject(deliveryTag, false);
            throw e;
        }
    }
}