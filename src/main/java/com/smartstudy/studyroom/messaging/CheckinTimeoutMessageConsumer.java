package com.smartstudy.studyroom.messaging;

import com.smartstudy.studyroom.config.RabbitMqConfig;
import com.smartstudy.studyroom.service.ReservationTimeoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CheckinTimeoutMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(
            CheckinTimeoutMessageConsumer.class
    );

    private final ReservationTimeoutService reservationTimeoutService;

    public CheckinTimeoutMessageConsumer(
            ReservationTimeoutService reservationTimeoutService) {

        this.reservationTimeoutService = reservationTimeoutService;
    }

    @RabbitListener(
            queues = RabbitMqConfig.CHECKIN_TIMEOUT_QUEUE,
            autoStartup = "${studyroom.rabbitmq.checkin-timeout.listener-auto-startup:false}"
    )
    public void consume(CheckinTimeoutMessage message) {
        boolean handled =
                reservationTimeoutService.handleCheckinTimeoutMessage(
                        message.reservationId(),
                        message.deadlineAt()
                );
        if (!handled) {
            log.debug(
                    "Ignored check-in timeout message, reservationId={}",
                    message.reservationId()
            );
        }
    }
}
