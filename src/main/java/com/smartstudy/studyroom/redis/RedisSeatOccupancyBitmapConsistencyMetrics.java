package com.smartstudy.studyroom.redis;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RedisSeatOccupancyBitmapConsistencyMetrics {

    private static final String BATCH_TOTAL =
            "studyroom.redis.seat_occupancy.consistency.batch.total";
    private static final String ROOMS_TOTAL =
            "studyroom.redis.seat_occupancy.consistency.rooms.total";
    private static final String DATES_TOTAL =
            "studyroom.redis.seat_occupancy.consistency.dates.total";
    private static final String CHECKED_TOTAL =
            "studyroom.redis.seat_occupancy.consistency.checked.total";
    private static final String CONSISTENT_TOTAL =
            "studyroom.redis.seat_occupancy.consistency.consistent.total";
    private static final String REBUILT_TOTAL =
            "studyroom.redis.seat_occupancy.consistency.rebuilt.total";
    private static final String SKIPPED_TOTAL =
            "studyroom.redis.seat_occupancy.consistency.skipped.total";
    private static final String FAILED_TOTAL =
            "studyroom.redis.seat_occupancy.consistency.failed.total";

    private final MeterRegistry meterRegistry;

    public RedisSeatOccupancyBitmapConsistencyMetrics(
            MeterRegistry meterRegistry) {

        this.meterRegistry = meterRegistry;
    }

    public void recordBatch(
            RedisSeatOccupancyBitmapBatchConsistencyService
                    .BatchReconcileResult result) {

        if (result == null) {
            increment(BATCH_TOTAL, 1, "reason", "null result");
            increment(FAILED_TOTAL, 1);
            return;
        }

        increment(BATCH_TOTAL, 1, "reason", result.reason());
        increment(ROOMS_TOTAL, result.rooms());
        increment(DATES_TOTAL, result.dates());
        increment(CHECKED_TOTAL, result.checked());
        increment(CONSISTENT_TOTAL, result.consistent());
        increment(REBUILT_TOTAL, result.rebuilt());
        increment(SKIPPED_TOTAL, result.skipped());
        increment(FAILED_TOTAL, result.failed());
    }

    private void increment(String name, double amount, String... tags) {
        if (amount <= 0) {
            return;
        }

        meterRegistry.counter(name, tags).increment(amount);
    }
}