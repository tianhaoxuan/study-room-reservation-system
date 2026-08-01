package com.smartstudy.studyroom.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class StudyRoomBusinessMetrics {

    public static final String REDIS_BITMAP_WRITE_TOTAL =
            "studyroom.redis.bitmap.write.total";
    public static final String REDIS_BITMAP_READ_TOTAL =
            "studyroom.redis.bitmap.read.total";
    public static final String REDIS_BITMAP_REBUILD_TOTAL =
            "studyroom.redis.bitmap.rebuild.total";
    public static final String RABBITMQ_OUTBOX_PUBLISH_TOTAL =
            "studyroom.rabbitmq.outbox.publish.total";
    public static final String RABBITMQ_CONFIRM_TOTAL =
            "studyroom.rabbitmq.confirm.total";
    public static final String RABBITMQ_RETURN_TOTAL =
            "studyroom.rabbitmq.return.total";
    public static final String RABBITMQ_OUTBOX_RETRY_BATCH_TOTAL =
            "studyroom.rabbitmq.outbox.retry.batch.total";
    public static final String RABBITMQ_OUTBOX_RETRY_MESSAGES_TOTAL =
            "studyroom.rabbitmq.outbox.retry.messages.total";

    private final MeterRegistry meterRegistry;

    public StudyRoomBusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRedisBitmapWrite(
            String operation,
            String result) {

        increment(
                REDIS_BITMAP_WRITE_TOTAL,
                "operation",
                normalize(operation),
                "result",
                normalize(result)
        );
    }

    public void recordRedisBitmapRead(String result) {
        increment(REDIS_BITMAP_READ_TOTAL, "result", normalize(result));
    }

    public void recordRedisBitmapRebuild(String result) {
        increment(REDIS_BITMAP_REBUILD_TOTAL, "result", normalize(result));
    }

    public void recordRabbitMqOutboxPublish(
            String source,
            String result) {

        increment(
                RABBITMQ_OUTBOX_PUBLISH_TOTAL,
                "source",
                normalize(source),
                "result",
                normalize(result)
        );
    }

    public void recordRabbitMqConfirm(String result) {
        increment(RABBITMQ_CONFIRM_TOTAL, "result", normalize(result));
    }

    public void recordRabbitMqReturn(String result) {
        increment(RABBITMQ_RETURN_TOTAL, "result", normalize(result));
    }

    public void recordOutboxRetryBatch(
            String result,
            int messageCount) {

        increment(
                RABBITMQ_OUTBOX_RETRY_BATCH_TOTAL,
                "result",
                normalize(result)
        );
        increment(
                RABBITMQ_OUTBOX_RETRY_MESSAGES_TOTAL,
                Math.max(messageCount, 0),
                "result",
                normalize(result)
        );
    }

    private void increment(String name, String... tags) {
        meterRegistry.counter(name, tags).increment();
    }

    private void increment(
            String name,
            double amount,
            String... tags) {

        if (amount <= 0) {
            return;
        }
        meterRegistry.counter(name, tags).increment(amount);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }
}
