package com.smartstudy.studyroom.task;

import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.messaging.CheckinTimeoutMessagePublisher;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        name = "studyroom.rabbitmq.outbox-retry.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationTimeoutMessageRetryTask {

    private final ReservationTimeoutMessageService messageService;
    private final CheckinTimeoutMessagePublisher publisher;
    private final int maxRetryCount;
    private final int batchSize;

    public ReservationTimeoutMessageRetryTask(
            ReservationTimeoutMessageService messageService,
            CheckinTimeoutMessagePublisher publisher,
            @Value("${studyroom.rabbitmq.outbox-retry.max-retry-count:5}")
            int maxRetryCount,
            @Value("${studyroom.rabbitmq.outbox-retry.batch-size:50}")
            int batchSize) {

        this.messageService = messageService;
        this.publisher = publisher;
        this.maxRetryCount = maxRetryCount;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${studyroom.rabbitmq.outbox-retry.fixed-delay-ms:60000}"
    )
    public void retryTimeoutMessages() {
        List<ReservationTimeoutMessage> messages =
                messageService.findRetryable(maxRetryCount, batchSize);

        for (ReservationTimeoutMessage message : messages) {
            publisher.publishOutboxMessage(message);
        }
    }
}