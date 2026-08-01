package com.smartstudy.studyroom.redis;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RedisSeatPreOccupyMetrics {

    private static final String PRE_OCCUPY_TOTAL =
            "studyroom.redis.seat_preoccupy.preoccupy.total";
    private static final String RELEASE_TOTAL =
            "studyroom.redis.seat_preoccupy.release.total";
    private static final String COMPENSATION_SCAN_TOTAL =
            "studyroom.redis.seat_preoccupy.compensation.scan.total";
    private static final String COMPENSATION_RELEASED_TOTAL =
            "studyroom.redis.seat_preoccupy.compensation.released.total";
    private static final String COMPENSATION_CONFIRMED_TOTAL =
            "studyroom.redis.seat_preoccupy.compensation.confirmed.total";
    private static final String COMPENSATION_INVALID_TOTAL =
            "studyroom.redis.seat_preoccupy.compensation.invalid.total";
    private static final String COMPENSATION_FAILED_TOTAL =
            "studyroom.redis.seat_preoccupy.compensation.failed.total";

    private final MeterRegistry meterRegistry;

    public RedisSeatPreOccupyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordPreOccupy(SeatPreOccupyStatus status) {
        increment(PRE_OCCUPY_TOTAL, "status", tag(status));
    }

    public void recordRelease(SeatPreOccupyStatus status) {
        increment(RELEASE_TOTAL, "status", tag(status));
    }

    public void recordCompensation(
            RedisSeatPreOccupyLeakCompensationService
                    .CompensationResult result) {

        if (result == null) {
            increment(COMPENSATION_SCAN_TOTAL, "result", "null");
            increment(COMPENSATION_FAILED_TOTAL);
            return;
        }

        increment(COMPENSATION_SCAN_TOTAL, "result", result.reason());
        increment(COMPENSATION_RELEASED_TOTAL, result.released());
        increment(COMPENSATION_CONFIRMED_TOTAL, result.confirmed());
        increment(COMPENSATION_INVALID_TOTAL, result.invalid());
        increment(COMPENSATION_FAILED_TOTAL, result.failed());
    }

    private void increment(String name, String... tags) {
        meterRegistry.counter(name, tags).increment();
    }

    private void increment(String name, double amount) {
        if (amount <= 0) {
            return;
        }
        meterRegistry.counter(name).increment(amount);
    }

    private String tag(SeatPreOccupyStatus status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return status.name();
    }
}