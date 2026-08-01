package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.RedisSeatPreOccupyService;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapKey;
import com.smartstudy.studyroom.redis.SeatPreOccupyKey;
import com.smartstudy.studyroom.redis.SeatPreOccupyResult;
import com.smartstudy.studyroom.redis.SeatPreOccupyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringJUnitConfig(
        classes = RedisSeatPreOccupyIntegrationTest.TestConfig.class
)
class RedisSeatPreOccupyIntegrationTest {

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID_1 = 3001L;
    private static final Long USER_ID_2 = 3002L;
    private static final Long SEAT_ID_1001 = 1001L;
    private static final Long SEAT_ID_1002 = 1002L;
    private static final LocalDate RESERVATION_DATE =
            LocalDate.of(2026, 8, 1);
    private static final List<Long> SLOT_IDS =
            List.of(2L, 3L);

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
            SeatPreOccupyKey.class
    })
    @ImportAutoConfiguration(RedisAutoConfiguration.class)
    static class TestConfig {

        @Bean
        RedisSeatPreOccupyService redisSeatPreOccupyService(
                StringRedisTemplate redisTemplate,
                SeatOccupancyBitmapKey bitmapKey,
                SeatPreOccupyKey preOccupyKey) {

            return new RedisSeatPreOccupyService(
                    redisTemplate,
                    bitmapKey,
                    preOccupyKey,
                    true,
                    Duration.ofMinutes(2)
            );
        }
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeatOccupancyBitmapKey bitmapKey;

    @Autowired
    private SeatPreOccupyKey preOccupyKey;

    @Autowired
    private RedisSeatPreOccupyService preOccupyService;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void shouldPreOccupySeatSlotsAtomically() {
        SeatPreOccupyResult result =
                preOccupyService.preOccupy(
                        "req-1",
                        USER_ID_1,
                        ROOM_ID,
                        RESERVATION_DATE,
                        SLOT_IDS,
                        SEAT_ID_1001
                );

        assertThat(result.status())
                .isEqualTo(SeatPreOccupyStatus.PREOCCUPIED);

        assertSeatBit(SLOT_IDS.get(0), SEAT_ID_1001, true);
        assertSeatBit(SLOT_IDS.get(1), SEAT_ID_1001, true);
        assertUserSlotBit(USER_ID_1, SLOT_IDS.get(0), true);
        assertUserSlotBit(USER_ID_1, SLOT_IDS.get(1), true);
    }

    @Test
    void shouldReturnIdempotentSuccessForSameRequestPayload() {
        preOccupyService.preOccupy(
                "req-1",
                USER_ID_1,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID_1001
        );

        SeatPreOccupyResult duplicate =
                preOccupyService.preOccupy(
                        "req-1",
                        USER_ID_1,
                        ROOM_ID,
                        RESERVATION_DATE,
                        List.of(3L, 2L),
                        SEAT_ID_1001
                );

        assertThat(duplicate.status())
                .isEqualTo(
                        SeatPreOccupyStatus.IDEMPOTENT_PREOCCUPIED
                );
    }

    @Test
    void shouldRejectSameRequestIdWithDifferentPayload() {
        preOccupyService.preOccupy(
                "req-1",
                USER_ID_1,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID_1001
        );

        SeatPreOccupyResult reused =
                preOccupyService.preOccupy(
                        "req-1",
                        USER_ID_1,
                        ROOM_ID,
                        RESERVATION_DATE,
                        SLOT_IDS,
                        SEAT_ID_1002
                );

        assertThat(reused.status())
                .isEqualTo(SeatPreOccupyStatus.REQUEST_CONFLICT);
    }

    @Test
    void shouldRejectSameUserOverlappingPreOccupy() {
        preOccupyService.preOccupy(
                "req-1",
                USER_ID_1,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID_1001
        );

        SeatPreOccupyResult result =
                preOccupyService.preOccupy(
                        "req-2",
                        USER_ID_1,
                        ROOM_ID,
                        RESERVATION_DATE,
                        List.of(3L, 4L),
                        SEAT_ID_1002
                );

        assertThat(result.status())
                .isEqualTo(SeatPreOccupyStatus.USER_CONFLICT);
    }

    @Test
    void shouldRejectSameSeatOverlappingPreOccupy() {
        preOccupyService.preOccupy(
                "req-1",
                USER_ID_1,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID_1001
        );

        SeatPreOccupyResult result =
                preOccupyService.preOccupy(
                        "req-2",
                        USER_ID_2,
                        ROOM_ID,
                        RESERVATION_DATE,
                        List.of(3L, 4L),
                        SEAT_ID_1001
                );

        assertThat(result.status())
                .isEqualTo(SeatPreOccupyStatus.SEAT_CONFLICT);
    }

    @Test
    void shouldAllowAdjacentNonOverlappingSlot() {
        preOccupyService.preOccupy(
                "req-1",
                USER_ID_1,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID_1001
        );

        SeatPreOccupyResult result =
                preOccupyService.preOccupy(
                        "req-2",
                        USER_ID_1,
                        ROOM_ID,
                        RESERVATION_DATE,
                        List.of(4L),
                        SEAT_ID_1001
                );

        assertThat(result.status())
                .isEqualTo(SeatPreOccupyStatus.PREOCCUPIED);
    }

    @Test
    void shouldReleasePreOccupiedSeatSlots() {
        preOccupyService.preOccupy(
                "req-1",
                USER_ID_1,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID_1001
        );

        SeatPreOccupyResult released =
                preOccupyService.release(
                        "req-1",
                        USER_ID_1,
                        ROOM_ID,
                        RESERVATION_DATE,
                        SLOT_IDS,
                        SEAT_ID_1001
                );

        assertThat(released.status())
                .isEqualTo(SeatPreOccupyStatus.RELEASED);

        assertSeatBit(SLOT_IDS.get(0), SEAT_ID_1001, false);
        assertSeatBit(SLOT_IDS.get(1), SEAT_ID_1001, false);
        assertUserSlotBit(USER_ID_1, SLOT_IDS.get(0), false);
        assertUserSlotBit(USER_ID_1, SLOT_IDS.get(1), false);
    }

    @Test
    void shouldTreatRepeatedReleaseAsIdempotent() {
        preOccupyService.preOccupy(
                "req-1",
                USER_ID_1,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID_1001
        );

        preOccupyService.release(
                "req-1",
                USER_ID_1,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID_1001
        );

        SeatPreOccupyResult repeated =
                preOccupyService.release(
                        "req-1",
                        USER_ID_1,
                        ROOM_ID,
                        RESERVATION_DATE,
                        SLOT_IDS,
                        SEAT_ID_1001
                );

        assertThat(repeated.status())
                .isEqualTo(SeatPreOccupyStatus.IDEMPOTENT_RELEASED);
    }

    private void assertSeatBit(
            Long slotId,
            Long seatId,
            boolean expected) {

        Boolean actual = redisTemplate.opsForValue().getBit(
                bitmapKey.forSlot(
                        ROOM_ID,
                        RESERVATION_DATE,
                        slotId
                ),
                seatId
        );

        assertThat(actual).isEqualTo(expected);
    }

    private void assertUserSlotBit(
            Long userId,
            Long slotId,
            boolean expected) {

        Boolean actual = redisTemplate.opsForValue().getBit(
                preOccupyKey.forUser(userId, RESERVATION_DATE),
                slotId
        );

        assertThat(actual).isEqualTo(expected);
    }
}