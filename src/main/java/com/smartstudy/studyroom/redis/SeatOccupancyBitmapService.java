package com.smartstudy.studyroom.redis;

import com.smartstudy.studyroom.metrics.StudyRoomBusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class SeatOccupancyBitmapService {

    private static final Logger log =
            LoggerFactory.getLogger(SeatOccupancyBitmapService.class);

    private final StringRedisTemplate redisTemplate;
    private final SeatOccupancyBitmapKey bitmapKey;
    private final int ttlDays;
    private final StudyRoomBusinessMetrics metrics;

    @Autowired
    public SeatOccupancyBitmapService(
            StringRedisTemplate redisTemplate,
            SeatOccupancyBitmapKey bitmapKey,
            @Value("${studyroom.redis.seat-occupancy.ttl-days:45}")
            int ttlDays,
            ObjectProvider<StudyRoomBusinessMetrics> metrics) {

        this(
                redisTemplate,
                bitmapKey,
                ttlDays,
                metrics.getIfAvailable()
        );
    }

    private SeatOccupancyBitmapService(
            StringRedisTemplate redisTemplate,
            SeatOccupancyBitmapKey bitmapKey,
            int ttlDays,
            StudyRoomBusinessMetrics metrics) {

        this.redisTemplate = redisTemplate;
        this.bitmapKey = bitmapKey;
        this.ttlDays = ttlDays;
        this.metrics = metrics;
    }

    public SeatOccupancyBitmapService(
            StringRedisTemplate redisTemplate,
            SeatOccupancyBitmapKey bitmapKey,
            int ttlDays) {

        this(
                redisTemplate,
                bitmapKey,
                ttlDays,
                (StudyRoomBusinessMetrics) null
        );
    }

    public void occupy(
            Long roomId,
            LocalDate reservationDate,
            Long slotId,
            Long seatId) {

        validateSeatId(seatId);
        String key = bitmapKey.forSlot(roomId, reservationDate, slotId);

        try {
            redisTemplate.opsForValue().setBit(key, seatId, true);
            redisTemplate.expire(key, ttl());
            recordWrite("occupy", "success");
        } catch (RuntimeException ex) {
            recordWrite("occupy", "failed");
            log.warn(
                    "Failed to set Redis seat occupancy bitmap, key={}, seatId={}",
                    key,
                    seatId,
                    ex
            );
        }
    }

    public void release(
            Long roomId,
            LocalDate reservationDate,
            Long slotId,
            Long seatId) {

        validateSeatId(seatId);
        String key = bitmapKey.forSlot(roomId, reservationDate, slotId);

        try {
            redisTemplate.opsForValue().setBit(key, seatId, false);
            redisTemplate.expire(key, ttl());
            recordWrite("release", "success");
        } catch (RuntimeException ex) {
            recordWrite("release", "failed");
            log.warn(
                    "Failed to clear Redis seat occupancy bitmap, key={}, seatId={}",
                    key,
                    seatId,
                    ex
            );
        }
    }

    public boolean rebuildSlot(
            Long roomId,
            LocalDate reservationDate,
            Long slotId,
            Collection<Long> occupiedSeatIds) {

        validateSlotId(slotId);
        validateOptionalSeatIds(occupiedSeatIds);

        String key = bitmapKey.forSlot(roomId, reservationDate, slotId);

        try {
            redisTemplate.delete(key);

            /*
             * Create the key even when this slot has no occupied seats.
             * This lets readers distinguish "empty projection" from
             * "missing projection".
             */
            redisTemplate.opsForValue().setBit(key, 0L, false);

            for (Long seatId : occupiedSeatIds) {
                redisTemplate.opsForValue().setBit(key, seatId, true);
            }

            redisTemplate.expire(key, ttl());
            recordRebuild("success");
            return true;
        } catch (RuntimeException ex) {
            recordRebuild("failed");
            log.warn(
                    "Failed to rebuild Redis seat occupancy bitmap, key={}, occupiedSeatIds={}",
                    key,
                    occupiedSeatIds,
                    ex
            );
            return false;
        }
    }

    public Optional<Set<Long>> findOccupiedSeatIds(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Collection<Long> seatIds) {

        validateSlotIds(slotIds);
        validateSeatIds(seatIds);

        try {
            if (!allSlotKeysExist(roomId, reservationDate, slotIds)) {
                recordRead("missing");
                return Optional.empty();
            }

            Set<Long> occupiedSeatIds = new LinkedHashSet<>();
            for (Long seatId : seatIds) {
                if (isOccupiedInAnySlot(
                        roomId,
                        reservationDate,
                        slotIds,
                        seatId
                )) {
                    occupiedSeatIds.add(seatId);
                }
            }
            recordRead("hit");
            return Optional.of(occupiedSeatIds);
        } catch (RuntimeException ex) {
            recordRead("failed");
            log.warn(
                    "Failed to read Redis seat occupancy bitmap, roomId={}, reservationDate={}, slotIds={}",
                    roomId,
                    reservationDate,
                    slotIds,
                    ex
            );
            return Optional.empty();
        }
    }

    private boolean allSlotKeysExist(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds) {

        for (Long slotId : slotIds) {
            String key = bitmapKey.forSlot(roomId, reservationDate, slotId);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                return false;
            }
        }
        return true;
    }

    private boolean isOccupiedInAnySlot(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Long seatId) {

        for (Long slotId : slotIds) {
            String key = bitmapKey.forSlot(
                    roomId,
                    reservationDate,
                    slotId
            );
            if (Boolean.TRUE.equals(
                    redisTemplate.opsForValue().getBit(key, seatId)
            )) {
                return true;
            }
        }
        return false;
    }

    private Duration ttl() {
        return Duration.ofDays(ttlDays);
    }

    private void validateSlotIds(Collection<Long> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "slotIds must not be empty"
            );
        }
        slotIds.forEach(this::validateSlotId);
    }

    private void validateSlotId(Long slotId) {
        if (slotId == null) {
            throw new IllegalArgumentException(
                    "slotId must not be null"
            );
        }
    }

    private void validateSeatIds(Collection<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "seatIds must not be empty"
            );
        }
        seatIds.forEach(this::validateSeatId);
    }

    private void validateOptionalSeatIds(Collection<Long> seatIds) {
        if (seatIds == null) {
            throw new IllegalArgumentException(
                    "seatIds must not be null"
            );
        }
        seatIds.forEach(this::validateSeatId);
    }

    private void validateSeatId(Long seatId) {
        if (seatId == null || seatId < 0) {
            throw new IllegalArgumentException(
                    "seatId must be a non-negative value"
            );
        }
    }

    private void recordWrite(String operation, String result) {
        if (metrics != null) {
            metrics.recordRedisBitmapWrite(operation, result);
        }
    }

    private void recordRead(String result) {
        if (metrics != null) {
            metrics.recordRedisBitmapRead(result);
        }
    }

    private void recordRebuild(String result) {
        if (metrics != null) {
            metrics.recordRedisBitmapRebuild(result);
        }
    }
}
