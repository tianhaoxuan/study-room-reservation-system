package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapRebuildLockService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisSeatOccupancyBitmapRebuildLockServiceTest {

    @Test
    void shouldRunActionAndUnlockWhenLockIsAcquired()
            throws InterruptedException {

        RedissonClient redissonClient =
                mock(RedissonClient.class);
        RLock lock =
                mock(RLock.class);
        RedisSeatOccupancyBitmapRebuildLockService service =
                new RedisSeatOccupancyBitmapRebuildLockService(
                        redissonClient,
                        true,
                        "lock:seat:bitmap:rebuild",
                        Duration.ofMillis(100),
                        Duration.ofSeconds(10)
                );

        when(redissonClient.getLock(
                "lock:seat:bitmap:rebuild:1:2026-08-01:2-3"
        )).thenReturn(lock);
        when(lock.tryLock(
                100,
                10000,
                TimeUnit.MILLISECONDS
        )).thenReturn(true);
        when(lock.isHeldByCurrentThread())
                .thenReturn(true);

        AtomicBoolean executed =
                new AtomicBoolean(false);

        boolean result =
                service.runWithLock(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of(3L, 2L, 2L),
                        () -> {
                            executed.set(true);
                            return true;
                        }
                );

        assertThat(result).isTrue();
        assertThat(executed).isTrue();

        verify(lock).unlock();
    }

    @Test
    void shouldReturnFalseWhenLockIsBusy()
            throws InterruptedException {

        RedissonClient redissonClient =
                mock(RedissonClient.class);
        RLock lock =
                mock(RLock.class);
        RedisSeatOccupancyBitmapRebuildLockService service =
                new RedisSeatOccupancyBitmapRebuildLockService(
                        redissonClient,
                        true,
                        "lock:seat:bitmap:rebuild",
                        Duration.ofMillis(100),
                        Duration.ofSeconds(10)
                );

        when(redissonClient.getLock(
                "lock:seat:bitmap:rebuild:1:2026-08-01:2-3"
        )).thenReturn(lock);
        when(lock.tryLock(
                100,
                10000,
                TimeUnit.MILLISECONDS
        )).thenReturn(false);

        AtomicBoolean executed =
                new AtomicBoolean(false);

        boolean result =
                service.runWithLock(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of(2L, 3L),
                        () -> {
                            executed.set(true);
                            return true;
                        }
                );

        assertThat(result).isFalse();
        assertThat(executed).isFalse();

        verify(lock, never()).unlock();
    }

    @Test
    void shouldRunActionDirectlyWhenLockIsDisabled() {
        RedissonClient redissonClient =
                mock(RedissonClient.class);
        RedisSeatOccupancyBitmapRebuildLockService service =
                new RedisSeatOccupancyBitmapRebuildLockService(
                        redissonClient,
                        false,
                        "lock:seat:bitmap:rebuild",
                        Duration.ofMillis(100),
                        Duration.ofSeconds(10)
                );

        boolean result =
                service.runWithLock(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of(2L, 3L),
                        () -> true
                );

        assertThat(result).isTrue();
        verifyNoInteractions(redissonClient);
    }

    @Test
    void shouldReturnFalseAndRestoreInterruptFlagWhenInterrupted()
            throws InterruptedException {

        RedissonClient redissonClient =
                mock(RedissonClient.class);
        RLock lock =
                mock(RLock.class);
        RedisSeatOccupancyBitmapRebuildLockService service =
                new RedisSeatOccupancyBitmapRebuildLockService(
                        redissonClient,
                        true,
                        "lock:seat:bitmap:rebuild",
                        Duration.ofMillis(100),
                        Duration.ofSeconds(10)
                );

        when(redissonClient.getLock(
                "lock:seat:bitmap:rebuild:1:2026-08-01:2-3"
        )).thenReturn(lock);
        when(lock.tryLock(
                100,
                10000,
                TimeUnit.MILLISECONDS
        )).thenThrow(new InterruptedException("interrupted"));

        boolean result =
                service.runWithLock(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of(2L, 3L),
                        () -> true
                );

        assertThat(result).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        Thread.interrupted();
    }

    @Test
    void shouldReturnFalseWhenInputIsInvalid() {
        RedissonClient redissonClient =
                mock(RedissonClient.class);
        RedisSeatOccupancyBitmapRebuildLockService service =
                new RedisSeatOccupancyBitmapRebuildLockService(
                        redissonClient,
                        true,
                        "lock:seat:bitmap:rebuild",
                        Duration.ofMillis(100),
                        Duration.ofSeconds(10)
                );

        boolean result =
                service.runWithLock(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of(),
                        () -> true
                );

        assertThat(result).isFalse();
        verifyNoInteractions(redissonClient);
    }
}