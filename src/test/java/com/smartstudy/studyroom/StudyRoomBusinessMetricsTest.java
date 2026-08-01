package com.smartstudy.studyroom;

import com.smartstudy.studyroom.metrics.StudyRoomBusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudyRoomBusinessMetricsTest {

    @Test
    void shouldRecordRedisBitmapMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StudyRoomBusinessMetrics metrics =
                new StudyRoomBusinessMetrics(registry);

        metrics.recordRedisBitmapWrite("occupy", "success");
        metrics.recordRedisBitmapRead("hit");
        metrics.recordRedisBitmapRebuild("failed");

        assertThat(registry.counter(
                StudyRoomBusinessMetrics.REDIS_BITMAP_WRITE_TOTAL,
                "operation",
                "occupy",
                "result",
                "success"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                StudyRoomBusinessMetrics.REDIS_BITMAP_READ_TOTAL,
                "result",
                "hit"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                StudyRoomBusinessMetrics.REDIS_BITMAP_REBUILD_TOTAL,
                "result",
                "failed"
        ).count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordRabbitMqMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StudyRoomBusinessMetrics metrics =
                new StudyRoomBusinessMetrics(registry);

        metrics.recordRabbitMqOutboxPublish("rabbit_template", "submitted");
        metrics.recordRabbitMqConfirm("ack");
        metrics.recordRabbitMqReturn("failed");
        metrics.recordOutboxRetryBatch("completed", 3);

        assertThat(registry.counter(
                StudyRoomBusinessMetrics.RABBITMQ_OUTBOX_PUBLISH_TOTAL,
                "source",
                "rabbit_template",
                "result",
                "submitted"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                StudyRoomBusinessMetrics.RABBITMQ_CONFIRM_TOTAL,
                "result",
                "ack"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                StudyRoomBusinessMetrics.RABBITMQ_RETURN_TOTAL,
                "result",
                "failed"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                StudyRoomBusinessMetrics.RABBITMQ_OUTBOX_RETRY_BATCH_TOTAL,
                "result",
                "completed"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                StudyRoomBusinessMetrics.RABBITMQ_OUTBOX_RETRY_MESSAGES_TOTAL,
                "result",
                "completed"
        ).count()).isEqualTo(3.0);
    }

    @Test
    void shouldNormalizeBlankMetricTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StudyRoomBusinessMetrics metrics =
                new StudyRoomBusinessMetrics(registry);

        metrics.recordRabbitMqConfirm("");

        assertThat(registry.counter(
                StudyRoomBusinessMetrics.RABBITMQ_CONFIRM_TOTAL,
                "result",
                "unknown"
        ).count()).isEqualTo(1.0);
    }
}
