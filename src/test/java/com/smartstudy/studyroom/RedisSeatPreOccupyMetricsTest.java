package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.RedisSeatPreOccupyLeakCompensationService;
import com.smartstudy.studyroom.redis.RedisSeatPreOccupyMetrics;
import com.smartstudy.studyroom.redis.SeatPreOccupyStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSeatPreOccupyMetricsTest {

    @Test
    void shouldRecordPreOccupyAndReleaseCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisSeatPreOccupyMetrics metrics =
                new RedisSeatPreOccupyMetrics(registry);

        metrics.recordPreOccupy(SeatPreOccupyStatus.PREOCCUPIED);
        metrics.recordPreOccupy(SeatPreOccupyStatus.SEAT_CONFLICT);
        metrics.recordRelease(SeatPreOccupyStatus.RELEASED);

        assertThat(registry.find(
                "studyroom.redis.seat_preoccupy.preoccupy.total"
        ).tag("status", "PREOCCUPIED").counter().count())
                .isEqualTo(1.0);

        assertThat(registry.find(
                "studyroom.redis.seat_preoccupy.preoccupy.total"
        ).tag("status", "SEAT_CONFLICT").counter().count())
                .isEqualTo(1.0);

        assertThat(registry.find(
                "studyroom.redis.seat_preoccupy.release.total"
        ).tag("status", "RELEASED").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void shouldRecordCompensationCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisSeatPreOccupyMetrics metrics =
                new RedisSeatPreOccupyMetrics(registry);

        metrics.recordCompensation(
                new RedisSeatPreOccupyLeakCompensationService
                        .CompensationResult(
                        true,
                        5,
                        2,
                        1,
                        1,
                        1,
                        "completed"
                )
        );

        assertThat(counter(registry,
                "studyroom.redis.seat_preoccupy.compensation.scan.total"))
                .isEqualTo(1.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_preoccupy.compensation.released.total"))
                .isEqualTo(2.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_preoccupy.compensation.confirmed.total"))
                .isEqualTo(1.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_preoccupy.compensation.invalid.total"))
                .isEqualTo(1.0);
        assertThat(counter(registry,
                "studyroom.redis.seat_preoccupy.compensation.failed.total"))
                .isEqualTo(1.0);
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String name) {

        return registry.find(name).counter().count();
    }
}