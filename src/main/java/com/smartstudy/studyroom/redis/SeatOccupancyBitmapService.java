package com.smartstudy.studyroom.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public SeatOccupancyBitmapService(
            StringRedisTemplate redisTemplate,
            SeatOccupancyBitmapKey bitmapKey,
            @Value("${studyroom.redis.seat-occupancy.ttl-days:45}")
            int ttlDays) {

        this.redisTemplate = redisTemplate;
        this.bitmapKey = bitmapKey;
        this.ttlDays = ttlDays;
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
        } catch (RuntimeException ex) {
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
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to clear Redis seat occupancy bitmap, key={}, seatId={}",
                    key,
                    seatId,
                    ex
            );
        }
    }

    public void occupyRange(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Long seatId) {

        validateSlotIds(slotIds);

        for (Long slotId : slotIds) {
            occupy(roomId, reservationDate, slotId, seatId);
        }
    }

    public void releaseRange(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Long seatId) {

        validateSlotIds(slotIds);

        for (Long slotId : slotIds) {
            release(roomId, reservationDate, slotId, seatId);
        }
    }

    public Optional<Set<Long>> findOccupiedSeatIds(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Collection<Long> seatIds) {

        validateSlotIds(slotIds);
        validateSeatIds(seatIds);

        Set<Long> occupiedSeatIds = new LinkedHashSet<>();

        try {
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
            return Optional.of(occupiedSeatIds);
        } catch (RuntimeException ex) {
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

            Boolean occupied =
                    redisTemplate.opsForValue().getBit(key, seatId);

            if (Boolean.TRUE.equals(occupied)) {
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
        if (slotIds.stream().anyMatch(slotId -> slotId == null)) {
            throw new IllegalArgumentException(
                    "slotIds must not contain null"
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

    private void validateSeatId(Long seatId) {
        if (seatId == null || seatId < 0) {
            throw new IllegalArgumentException(
                    "seatId must be a non-negative value"
            );
        }
    }
}