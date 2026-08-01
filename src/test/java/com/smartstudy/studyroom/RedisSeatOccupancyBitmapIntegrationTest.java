package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.SeatOccupancyBitmapKey;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringJUnitConfig(
        classes = RedisSeatOccupancyBitmapIntegrationTest.TestConfig.class
)
@TestPropertySource(properties = {
        "studyroom.redis.seat-occupancy.key-prefix=seat:bitmap:test",
        "studyroom.redis.seat-occupancy.ttl-days=1"
})
class RedisSeatOccupancyBitmapIntegrationTest {

    private static final LocalDate RESERVATION_DATE =
            LocalDate.of(2026, 8, 1);

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.2-alpine")
            ).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> redis.getMappedPort(6379)
        );
    }

    @TestConfiguration
    @Import({
            SeatOccupancyBitmapKey.class,
            SeatOccupancyBitmapService.class
    })
    @ImportAutoConfiguration(RedisAutoConfiguration.class)
    static class TestConfig {
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeatOccupancyBitmapService bitmapService;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void shouldWriteAndReadOccupiedSeatFromRealRedisBitmap() {
        bitmapService.occupy(
                1L,
                RESERVATION_DATE,
                2L,
                1001L
        );

        Optional<Set<Long>> occupiedSeatIds =
                bitmapService.findOccupiedSeatIds(
                        1L,
                        RESERVATION_DATE,
                        List.of(2L),
                        List.of(1001L, 1002L)
                );

        assertThat(occupiedSeatIds)
                .hasValue(Set.of(1001L));
    }

    @Test
    void shouldReadSeatOccupiedInAnySelectedSlotWhenProjectionIsComplete() {
        touchSlotBitmapKey(2L);
        touchSlotBitmapKey(4L);

        bitmapService.occupy(
                1L,
                RESERVATION_DATE,
                3L,
                1001L
        );

        Optional<Set<Long>> occupiedSeatIds =
                bitmapService.findOccupiedSeatIds(
                        1L,
                        RESERVATION_DATE,
                        List.of(2L, 3L, 4L),
                        List.of(1001L, 1002L)
                );

        assertThat(occupiedSeatIds)
                .hasValue(Set.of(1001L));
    }

    @Test
    void shouldReturnProjectionMissingWhenAnySlotKeyDoesNotExist() {
        bitmapService.occupy(
                1L,
                RESERVATION_DATE,
                2L,
                1001L
        );

        Optional<Set<Long>> occupiedSeatIds =
                bitmapService.findOccupiedSeatIds(
                        1L,
                        RESERVATION_DATE,
                        List.of(2L, 3L),
                        List.of(1001L)
                );

        assertThat(occupiedSeatIds).isEmpty();
    }

    @Test
    void shouldReleaseOccupiedSeatFromRealRedisBitmap() {
        bitmapService.occupy(
                1L,
                RESERVATION_DATE,
                2L,
                1001L
        );

        bitmapService.release(
                1L,
                RESERVATION_DATE,
                2L,
                1001L
        );

        Optional<Set<Long>> occupiedSeatIds =
                bitmapService.findOccupiedSeatIds(
                        1L,
                        RESERVATION_DATE,
                        List.of(2L),
                        List.of(1001L)
                );

        assertThat(occupiedSeatIds)
                .hasValue(Set.of());
    }

    private void touchSlotBitmapKey(Long slotId) {
        redisTemplate.opsForValue().setBit(
                "seat:bitmap:test:1:" + RESERVATION_DATE + ":" + slotId,
                0L,
                false
        );
    }
}