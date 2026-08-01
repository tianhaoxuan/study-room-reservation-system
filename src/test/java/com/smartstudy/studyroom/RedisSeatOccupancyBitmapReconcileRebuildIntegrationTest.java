package com.smartstudy.studyroom;

import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapConsistencyService;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapRebuildLockService;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapRebuildService;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapKey;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringJUnitConfig(
        classes = RedisSeatOccupancyBitmapReconcileRebuildIntegrationTest
                .TestConfig.class
)
@TestPropertySource(properties = {
        "studyroom.redis.seat-occupancy.key-prefix=seat:bitmap:reconcile:test",
        "studyroom.redis.seat-occupancy.ttl-days=1",
        "studyroom.redis.seat-occupancy.consistency.enabled=true"
})
class RedisSeatOccupancyBitmapReconcileRebuildIntegrationTest {

    private static final Long ROOM_ID = 1L;
    private static final Long SLOT_ID_2 = 2L;
    private static final Long SLOT_ID_3 = 3L;
    private static final Long SEAT_ID_1001 = 1001L;
    private static final Long SEAT_ID_1002 = 1002L;
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
            SeatOccupancyBitmapService.class,
            RedisSeatOccupancyBitmapRebuildService.class,
            RedisSeatOccupancyBitmapConsistencyService.class
    })
    @ImportAutoConfiguration(RedisAutoConfiguration.class)
    static class TestConfig {

        @Bean
        SeatMapper seatMapper() {
            return Mockito.mock(SeatMapper.class);
        }

        @Bean
        ReservationSlotOccupancyMapper reservationSlotOccupancyMapper() {
            return Mockito.mock(ReservationSlotOccupancyMapper.class);
        }

        @Bean
        RedisSeatOccupancyBitmapRebuildLockService
        redisSeatOccupancyBitmapRebuildLockService() {

            return new RedisSeatOccupancyBitmapRebuildLockService(
                    Mockito.mock(RedissonClient.class),
                    false,
                    "lock:seat:bitmap:reconcile:test",
                    Duration.ofMillis(500),
                    Duration.ofSeconds(5)
            );
        }
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeatOccupancyBitmapKey bitmapKey;

    @Autowired
    private SeatOccupancyBitmapService bitmapService;

    @Autowired
    private RedisSeatOccupancyBitmapConsistencyService consistencyService;

    @Autowired
    private SeatMapper seatMapper;

    @Autowired
    private ReservationSlotOccupancyMapper occupancyMapper;

    @BeforeEach
    void cleanRedisAndMocks() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        Mockito.reset(seatMapper, occupancyMapper);
    }

    @Test
    void shouldRebuildMissingProjectionFromMysqlOccupancy() {
        givenRoomSeats();
        givenMysqlOccupancySeat1001InSlot2();

        RedisSeatOccupancyBitmapConsistencyService.ReconcileResult result =
                consistencyService.reconcile(
                        ROOM_ID,
                        RESERVATION_DATE,
                        List.of(SLOT_ID_2, SLOT_ID_3)
                );

        assertThat(result.checked()).isTrue();
        assertThat(result.consistent()).isFalse();
        assertThat(result.rebuilt()).isTrue();
        assertThat(result.reason())
                .isEqualTo("redis projection missing");

        Optional<Set<Long>> occupiedSeatIds =
                bitmapService.findOccupiedSeatIds(
                        ROOM_ID,
                        RESERVATION_DATE,
                        List.of(SLOT_ID_2, SLOT_ID_3),
                        List.of(SEAT_ID_1001, SEAT_ID_1002)
                );

        assertThat(occupiedSeatIds)
                .hasValue(Set.of(SEAT_ID_1001));
    }

    @Test
    void shouldRebuildInconsistentProjectionFromMysqlOccupancy() {
        givenRoomSeats();
        givenMysqlOccupancySeat1001InSlot2();

        bitmapService.occupy(
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_ID_2,
                SEAT_ID_1002
        );
        touchSlotBitmapKey(SLOT_ID_3);

        RedisSeatOccupancyBitmapConsistencyService.ReconcileResult result =
                consistencyService.reconcile(
                        ROOM_ID,
                        RESERVATION_DATE,
                        List.of(SLOT_ID_2, SLOT_ID_3)
                );

        assertThat(result.checked()).isTrue();
        assertThat(result.consistent()).isFalse();
        assertThat(result.rebuilt()).isTrue();
        assertThat(result.reason())
                .isEqualTo("redis projection inconsistent");
        assertThat(result.mysqlOccupiedSeatIds())
                .isEqualTo(Set.of(SEAT_ID_1001));
        assertThat(result.redisOccupiedSeatIds())
                .isEqualTo(Set.of(SEAT_ID_1002));

        Optional<Set<Long>> occupiedSeatIds =
                bitmapService.findOccupiedSeatIds(
                        ROOM_ID,
                        RESERVATION_DATE,
                        List.of(SLOT_ID_2, SLOT_ID_3),
                        List.of(SEAT_ID_1001, SEAT_ID_1002)
                );

        assertThat(occupiedSeatIds)
                .hasValue(Set.of(SEAT_ID_1001));
    }

    private void givenRoomSeats() {
        when(seatMapper.findByRoomId(ROOM_ID))
                .thenReturn(List.of(
                        seat(SEAT_ID_1001),
                        seat(SEAT_ID_1002)
                ));
    }

    private void givenMysqlOccupancySeat1001InSlot2() {
        when(occupancyMapper.findByRoomDateAndSlotIds(
                ROOM_ID,
                RESERVATION_DATE,
                List.of(SLOT_ID_2, SLOT_ID_3)
        )).thenReturn(List.of(
                occupancy(SEAT_ID_1001, SLOT_ID_2)
        ));
    }

    private void touchSlotBitmapKey(Long slotId) {
        redisTemplate.opsForValue().setBit(
                bitmapKey.forSlot(
                        ROOM_ID,
                        RESERVATION_DATE,
                        slotId
                ),
                0L,
                false
        );
    }

    private static Seat seat(Long seatId) {
        Seat seat = new Seat();
        seat.setId(seatId);
        seat.setRoomId(ROOM_ID);
        return seat;
    }

    private static ReservationSlotOccupancy occupancy(
            Long seatId,
            Long slotId) {

        ReservationSlotOccupancy occupancy =
                new ReservationSlotOccupancy();
        occupancy.setReservationId(9001L);
        occupancy.setUserId(3001L);
        occupancy.setRoomId(ROOM_ID);
        occupancy.setSeatId(seatId);
        occupancy.setReservationDate(RESERVATION_DATE);
        occupancy.setSlotId(slotId);
        return occupancy;
    }
}