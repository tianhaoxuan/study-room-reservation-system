package com.smartstudy.studyroom.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class RedisSeatOccupancyBitmapRebuildLockService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RedisSeatOccupancyBitmapRebuildLockService.class
            );

    private final RedissonClient redissonClient;
    private final boolean enabled;
    private final String keyPrefix;
    private final Duration waitTime;
    private final Duration leaseTime;

    public RedisSeatOccupancyBitmapRebuildLockService(
            RedissonClient redissonClient,
            @Value("${studyroom.redis.seat-occupancy.rebuild-lock.enabled:true}")
            boolean enabled,
            @Value("${studyroom.redis.seat-occupancy.rebuild-lock.key-prefix:lock:seat:bitmap:rebuild}")
            String keyPrefix,
            @Value("${studyroom.redis.seat-occupancy.rebuild-lock.wait-time:100ms}")
            Duration waitTime,
            @Value("${studyroom.redis.seat-occupancy.rebuild-lock.lease-time:10s}")
            Duration leaseTime) {

        this.redissonClient = redissonClient;
        this.enabled = enabled;
        this.keyPrefix = keyPrefix;
        this.waitTime = waitTime;
        this.leaseTime = leaseTime;
    }

    public boolean runWithLock(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Supplier<Boolean> action) {

        if (roomId == null
                || reservationDate == null
                || slotIds == null
                || slotIds.isEmpty()
                || action == null) {
            return false;
        }

        if (!enabled) {
            return Boolean.TRUE.equals(action.get());
        }

        String lockKey =
                buildLockKey(roomId, reservationDate, slotIds);
        RLock lock =
                redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            locked = lock.tryLock(
                    waitTime.toMillis(),
                    leaseTime.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS
            );

            if (!locked) {
                log.info(
                        "Skip Redis seat occupancy bitmap rebuild because lock is busy, lockKey={}",
                        lockKey
                );
                return false;
            }

            return Boolean.TRUE.equals(action.get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Interrupted while acquiring Redis seat occupancy bitmap rebuild lock, lockKey={}",
                    lockKey,
                    ex
            );
            return false;
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to rebuild Redis seat occupancy bitmap with lock, lockKey={}",
                    lockKey,
                    ex
            );
            return false;
        } finally {
            unlockIfHeld(lockKey, lock, locked);
        }
    }

    private String buildLockKey(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds) {

        String slotPart =
                slotIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining("-"));

        return keyPrefix
                + ":"
                + roomId
                + ":"
                + reservationDate
                + ":"
                + slotPart;
    }

    private void unlockIfHeld(
            String lockKey,
            RLock lock,
            boolean locked) {

        if (!locked) {
            return;
        }

        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to release Redis seat occupancy bitmap rebuild lock, lockKey={}",
                    lockKey,
                    ex
            );
        }
    }
}