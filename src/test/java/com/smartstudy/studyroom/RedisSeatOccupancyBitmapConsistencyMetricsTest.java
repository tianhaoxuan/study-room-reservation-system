package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapBatchConsistencyService;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapConsistencyMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSeatOccupancyBitmapConsistencyMetricsTest {

    @Test
    void shouldRecordBatchReconcileCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisSeatOccupancyBitmapConsistencyMetrics metrics =
                new RedisSeatOccupancyBitmapConsistencyMetrics(registry);

        RedisSeatOccupancyBitmapBatchConsistencyService.BatchReconcileResult
                result =
                new RedisSeatOccupancyBitmapBatchConsistencyService
                        .BatchReconcileResult(
                        2,
                        3,
                        6,
                        4,
                        1,
                        1,
                        0,
                        "completed"
                );

        metrics.recordBatch(result);

        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.batch.total"))
                .isEqualTo(1.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.rooms.total"))
                .isEqualTo(2.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.dates.total"))
                .isEqualTo(3.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.checked.total"))
                .isEqualTo(6.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.consistent.total"))
                .isEqualTo(4.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.rebuilt.total"))
                .isEqualTo(1.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.skipped.total"))
                .isEqualTo(1.0);
        assertThat(registry.find(
                "studyroom.redis.seat_occupancy.consistency.failed.total"
        ).counter()).isNull();
    }

    @Test
    void shouldRecordNullResultAsFailedBatch() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisSeatOccupancyBitmapConsistencyMetrics metrics =
                new RedisSeatOccupancyBitmapConsistencyMetrics(registry);

        metrics.recordBatch(null);

        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.batch.total"))
                .isEqualTo(1.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_occupancy.consistency.failed.total"))
                .isEqualTo(1.0);
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String name) {

        return registry.find(name).counter().count();
    }
}