package com.smartstudy.studyroom.redis;

import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class RedisSeatPreOccupyLeakCompensationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RedisSeatPreOccupyLeakCompensationService.class
            );

    private final StringRedisTemplate redisTemplate;
    private final SeatPreOccupyKey preOccupyKey;
    private final ReservationMapper reservationMapper;
    private final RedisSeatPreOccupyService preOccupyService;
    private final RedisSeatPreOccupyMetrics metrics;
    private final boolean enabled;

    public RedisSeatPreOccupyLeakCompensationService(
            StringRedisTemplate redisTemplate,
            SeatPreOccupyKey preOccupyKey,
            ReservationMapper reservationMapper,
            RedisSeatPreOccupyService preOccupyService,
            RedisSeatPreOccupyMetrics metrics,
            @Value("${studyroom.redis.seat-preoccupy.compensation.enabled:true}")
            boolean enabled) {

        this.redisTemplate = redisTemplate;
        this.preOccupyKey = preOccupyKey;
        this.reservationMapper = reservationMapper;
        this.preOccupyService = preOccupyService;
        this.metrics = metrics;
        this.enabled = enabled;
    }

    public CompensationResult compensate(int limit) {
        if (!enabled) {
            return recordAndReturn(
                    CompensationResult.skipped("disabled")
            );
        }
        if (limit <= 0) {
            return recordAndReturn(
                    CompensationResult.skipped("invalid limit")
            );
        }

        MutableResult result = new MutableResult();

        try {
            redisTemplate.execute((RedisConnection connection) -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(preOccupyKey.requestPattern())
                        .count(Math.min(limit, 1000))
                        .build();

                try (Cursor<byte[]> cursor = connection.keyCommands()
                        .scan(options)) {

                    while (cursor.hasNext()
                            && result.scanned < limit) {

                        String key = new String(
                                cursor.next(),
                                StandardCharsets.UTF_8
                        );
                        handleRequestKey(key, result);
                    }
                }
                return null;
            });

            return recordAndReturn(result.toResult("completed"));
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to compensate Redis seat pre-occupy leaks",
                    ex
            );
            result.failed++;
            return recordAndReturn(result.toResult("exception"));
        }
    }

    private void handleRequestKey(
            String requestKey,
            MutableResult result) {

        result.scanned++;

        String value = redisTemplate.opsForValue().get(requestKey);
        Optional<SeatPreOccupyPayload> payload =
                SeatPreOccupyPayload.parse(value);

        if (payload.isEmpty()) {
            result.invalid++;
            return;
        }

        SeatPreOccupyPayload parsed = payload.get();
        Reservation reservation =
                reservationMapper.findByUserIdAndRequestId(
                        parsed.userId(),
                        parsed.requestId()
                );

        if (reservation != null) {
            result.confirmed++;
            return;
        }

        SeatPreOccupyResult releaseResult =
                preOccupyService.release(
                        parsed.requestId(),
                        parsed.userId(),
                        parsed.roomId(),
                        parsed.reservationDate(),
                        parsed.slotIds(),
                        parsed.seatId()
                );

        if (releaseResult.status() == SeatPreOccupyStatus.RELEASED
                || releaseResult.status()
                == SeatPreOccupyStatus.IDEMPOTENT_RELEASED) {
            result.released++;
            return;
        }

        result.failed++;
    }

    private CompensationResult recordAndReturn(
            CompensationResult result) {

        metrics.recordCompensation(result);
        return result;
    }

    public record CompensationResult(
            boolean checked,
            int scanned,
            int released,
            int confirmed,
            int invalid,
            int failed,
            String reason) {

        static CompensationResult skipped(String reason) {
            return new CompensationResult(
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    reason
            );
        }
    }

    private static final class MutableResult {

        private int scanned;
        private int released;
        private int confirmed;
        private int invalid;
        private int failed;

        private CompensationResult toResult(String reason) {
            return new CompensationResult(
                    true,
                    scanned,
                    released,
                    confirmed,
                    invalid,
                    failed,
                    reason
            );
        }
    }
}