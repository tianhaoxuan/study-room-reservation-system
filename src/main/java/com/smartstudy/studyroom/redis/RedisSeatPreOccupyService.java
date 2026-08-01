package com.smartstudy.studyroom.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class RedisSeatPreOccupyService {

    private static final Logger log =
            LoggerFactory.getLogger(RedisSeatPreOccupyService.class);

    private static final RedisScript<String> PRE_OCCUPY_SCRIPT =
            RedisScript.of("""
                    local requestKey = KEYS[1]
                    local userKey = KEYS[2]
                    local payload = ARGV[1]
                    local seatId = tonumber(ARGV[2])
                    local ttlSeconds = tonumber(ARGV[3])
                    local slotCount = tonumber(ARGV[4])

                    local existingPayload = redis.call('GET', requestKey)
                    if existingPayload then
                        if existingPayload == payload then
                            return 'IDEMPOTENT_PREOCCUPIED'
                        end
                        return 'REQUEST_CONFLICT'
                    end

                    for i = 1, slotCount do
                        local slotId = tonumber(ARGV[4 + i])
                        if redis.call('GETBIT', userKey, slotId) == 1 then
                            return 'USER_CONFLICT'
                        end
                    end

                    for i = 1, slotCount do
                        local seatBitmapKey = KEYS[2 + i]
                        if redis.call('GETBIT', seatBitmapKey, seatId) == 1 then
                            return 'SEAT_CONFLICT'
                        end
                    end

                    for i = 1, slotCount do
                        local slotId = tonumber(ARGV[4 + i])
                        local seatBitmapKey = KEYS[2 + i]
                        redis.call('SETBIT', userKey, slotId, 1)
                        redis.call('SETBIT', seatBitmapKey, seatId, 1)
                        redis.call('EXPIRE', seatBitmapKey, ttlSeconds)
                    end

                    redis.call('EXPIRE', userKey, ttlSeconds)
                    redis.call('SET', requestKey, payload, 'EX', ttlSeconds)
                    return 'PREOCCUPIED'
                    """, String.class);

    private static final RedisScript<String> RELEASE_SCRIPT =
            RedisScript.of("""
                    local requestKey = KEYS[1]
                    local userKey = KEYS[2]
                    local payload = ARGV[1]
                    local seatId = tonumber(ARGV[2])
                    local slotCount = tonumber(ARGV[3])

                    local existingPayload = redis.call('GET', requestKey)
                    if not existingPayload then
                        return 'IDEMPOTENT_RELEASED'
                    end

                    if existingPayload ~= payload then
                        return 'REQUEST_CONFLICT'
                    end

                    for i = 1, slotCount do
                        local slotId = tonumber(ARGV[3 + i])
                        local seatBitmapKey = KEYS[2 + i]
                        redis.call('SETBIT', userKey, slotId, 0)
                        redis.call('SETBIT', seatBitmapKey, seatId, 0)
                    end

                    redis.call('DEL', requestKey)
                    return 'RELEASED'
                    """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final SeatOccupancyBitmapKey bitmapKey;
    private final SeatPreOccupyKey preOccupyKey;
    private final boolean enabled;
    private final Duration ttl;

    public RedisSeatPreOccupyService(
            StringRedisTemplate redisTemplate,
            SeatOccupancyBitmapKey bitmapKey,
            SeatPreOccupyKey preOccupyKey,
            @Value("${studyroom.redis.seat-preoccupy.enabled:true}")
            boolean enabled,
            @Value("${studyroom.redis.seat-preoccupy.ttl:2m}")
            Duration ttl) {

        this.redisTemplate = redisTemplate;
        this.bitmapKey = bitmapKey;
        this.preOccupyKey = preOccupyKey;
        this.enabled = enabled;
        this.ttl = ttl;
    }

    public SeatPreOccupyResult preOccupy(
            String requestId,
            Long userId,
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Long seatId) {

        if (!enabled) {
            return SeatPreOccupyResult.of(
                    SeatPreOccupyStatus.DISABLED,
                    "Redis seat pre-occupy is disabled"
            );
        }

        ValidationResult validation =
                validate(requestId, userId, roomId,
                        reservationDate, slotIds, seatId);
        if (!validation.isValid()) {
            return SeatPreOccupyResult.of(
                    SeatPreOccupyStatus.INVALID,
                    validation.message()
            );
        }

        List<Long> orderedSlotIds = normalizeSlotIds(slotIds);
        List<String> keys =
                buildKeys(requestId, userId, roomId,
                        reservationDate, orderedSlotIds);
        List<String> args =
                buildPreOccupyArgs(requestId, userId, roomId,
                        reservationDate, orderedSlotIds, seatId);

        try {
            String status = redisTemplate.execute(
                    PRE_OCCUPY_SCRIPT,
                    keys,
                    args.toArray()
            );
            return toResult(status);
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to pre-occupy seat by Redis Lua, requestId={}, userId={}, roomId={}, reservationDate={}, slotIds={}, seatId={}",
                    requestId,
                    userId,
                    roomId,
                    reservationDate,
                    orderedSlotIds,
                    seatId,
                    ex
            );
            return SeatPreOccupyResult.of(
                    SeatPreOccupyStatus.FAILED,
                    "Redis seat pre-occupy failed"
            );
        }
    }

    public SeatPreOccupyResult release(
            String requestId,
            Long userId,
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Long seatId) {

        if (!enabled) {
            return SeatPreOccupyResult.of(
                    SeatPreOccupyStatus.DISABLED,
                    "Redis seat pre-occupy is disabled"
            );
        }

        ValidationResult validation =
                validate(requestId, userId, roomId,
                        reservationDate, slotIds, seatId);
        if (!validation.isValid()) {
            return SeatPreOccupyResult.of(
                    SeatPreOccupyStatus.INVALID,
                    validation.message()
            );
        }

        List<Long> orderedSlotIds = normalizeSlotIds(slotIds);
        List<String> keys =
                buildKeys(requestId, userId, roomId,
                        reservationDate, orderedSlotIds);
        List<String> args =
                buildReleaseArgs(requestId, userId, roomId,
                        reservationDate, orderedSlotIds, seatId);

        try {
            String status = redisTemplate.execute(
                    RELEASE_SCRIPT,
                    keys,
                    args.toArray()
            );
            return toResult(status);
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to release Redis Lua seat pre-occupy, requestId={}, userId={}, roomId={}, reservationDate={}, slotIds={}, seatId={}",
                    requestId,
                    userId,
                    roomId,
                    reservationDate,
                    orderedSlotIds,
                    seatId,
                    ex
            );
            return SeatPreOccupyResult.of(
                    SeatPreOccupyStatus.FAILED,
                    "Redis seat pre-occupy release failed"
            );
        }
    }

    private ValidationResult validate(
            String requestId,
            Long userId,
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Long seatId) {

        if (requestId == null || requestId.isBlank()) {
            return ValidationResult.invalid("requestId must not be blank");
        }
        if (userId == null) {
            return ValidationResult.invalid("userId must not be null");
        }
        if (roomId == null) {
            return ValidationResult.invalid("roomId must not be null");
        }
        if (reservationDate == null) {
            return ValidationResult.invalid(
                    "reservationDate must not be null"
            );
        }
        if (seatId == null || seatId < 0) {
            return ValidationResult.invalid(
                    "seatId must be a non-negative value"
            );
        }
        if (slotIds == null || slotIds.isEmpty()) {
            return ValidationResult.invalid("slotIds must not be empty");
        }
        if (normalizeSlotIds(slotIds).isEmpty()) {
            return ValidationResult.invalid("slotIds must not be empty");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return ValidationResult.invalid("ttl must be positive");
        }
        return ValidationResult.ok();
    }

    private List<Long> normalizeSlotIds(Collection<Long> slotIds) {
        return slotIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> buildKeys(
            String requestId,
            Long userId,
            Long roomId,
            LocalDate reservationDate,
            List<Long> slotIds) {

        List<String> keys = new ArrayList<>();
        keys.add(preOccupyKey.forRequest(requestId));
        keys.add(preOccupyKey.forUser(userId, reservationDate));

        for (Long slotId : slotIds) {
            keys.add(bitmapKey.forSlot(
                    roomId,
                    reservationDate,
                    slotId
            ));
        }

        return keys;
    }

    private List<String> buildPreOccupyArgs(
            String requestId,
            Long userId,
            Long roomId,
            LocalDate reservationDate,
            List<Long> slotIds,
            Long seatId) {

        List<String> args = new ArrayList<>();
        args.add(payload(
                requestId,
                userId,
                roomId,
                reservationDate,
                slotIds,
                seatId
        ));
        args.add(String.valueOf(seatId));
        args.add(String.valueOf(ttl.toSeconds()));
        args.add(String.valueOf(slotIds.size()));

        for (Long slotId : slotIds) {
            args.add(String.valueOf(slotId));
        }

        return args;
    }

    private List<String> buildReleaseArgs(
            String requestId,
            Long userId,
            Long roomId,
            LocalDate reservationDate,
            List<Long> slotIds,
            Long seatId) {

        List<String> args = new ArrayList<>();
        args.add(payload(
                requestId,
                userId,
                roomId,
                reservationDate,
                slotIds,
                seatId
        ));
        args.add(String.valueOf(seatId));
        args.add(String.valueOf(slotIds.size()));

        for (Long slotId : slotIds) {
            args.add(String.valueOf(slotId));
        }

        return args;
    }

    private String payload(
            String requestId,
            Long userId,
            Long roomId,
            LocalDate reservationDate,
            List<Long> slotIds,
            Long seatId) {

        return requestId.strip()
                + "|"
                + userId
                + "|"
                + roomId
                + "|"
                + reservationDate
                + "|"
                + seatId
                + "|"
                + String.join(
                ",",
                slotIds.stream()
                        .map(String::valueOf)
                        .toList()
        );
    }

    private SeatPreOccupyResult toResult(String status) {
        if (status == null || status.isBlank()) {
            return SeatPreOccupyResult.of(
                    SeatPreOccupyStatus.FAILED,
                    "Redis Lua returned empty status"
            );
        }

        try {
            SeatPreOccupyStatus parsed =
                    SeatPreOccupyStatus.valueOf(status);
            return SeatPreOccupyResult.of(
                    parsed,
                    message(parsed)
            );
        } catch (IllegalArgumentException ex) {
            return SeatPreOccupyResult.of(
                    SeatPreOccupyStatus.FAILED,
                    "Unknown Redis Lua status: " + status
            );
        }
    }

    private String message(SeatPreOccupyStatus status) {
        return switch (status) {
            case PREOCCUPIED -> "Seat pre-occupied";
            case IDEMPOTENT_PREOCCUPIED ->
                    "Seat pre-occupy request already succeeded";
            case USER_CONFLICT ->
                    "User has overlapping pre-occupied slot";
            case SEAT_CONFLICT ->
                    "Seat has been occupied or pre-occupied";
            case REQUEST_CONFLICT ->
                    "Request id was reused with different payload";
            case RELEASED -> "Seat pre-occupy released";
            case IDEMPOTENT_RELEASED ->
                    "Seat pre-occupy request has already been released";
            case DISABLED -> "Redis seat pre-occupy is disabled";
            case INVALID -> "Invalid Redis seat pre-occupy input";
            case FAILED -> "Redis seat pre-occupy failed";
        };
    }

    private static final class ValidationResult {

        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        private boolean isValid() {
            return valid;
        }

        private String message() {
            return message;
        }

        private static ValidationResult ok() {
            return new ValidationResult(true, "valid");
        }

        private static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}