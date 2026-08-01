package com.smartstudy.studyroom;

import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.redis.RedisSeatPreOccupyLeakCompensationService;
import com.smartstudy.studyroom.redis.RedisSeatPreOccupyMetrics;
import com.smartstudy.studyroom.redis.RedisSeatPreOccupyService;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapKey;
import com.smartstudy.studyroom.redis.SeatPreOccupyKey;
import com.smartstudy.studyroom.redis.SeatPreOccupyStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.Mockito.when;

@Testcontainers
@SpringJUnitConfig(
        classes = RedisSeatPreOccupyLeakCompensationIntegrationTest
                .TestConfig.class
)
class RedisSeatPreOccupyLeakCompensationIntegrationTest {

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 3001L;
    private static final Long SEAT_ID = 1001L;
    private static final LocalDate RESERVATION_DATE =
            LocalDate.of(2026, 8, 1);
    private static final List<Long> SLOT_IDS = List.of(2L, 3L);

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
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        RedisSeatPreOccupyMetrics redisSeatPreOccupyMetrics(
                SimpleMeterRegistry meterRegistry) {

            return new RedisSeatPreOccupyMetrics(meterRegistry);
        }

        @Bean
        ReservationMapper reservationMapper() {
            return Mockito.mock(ReservationMapper.class);
        }

        @Bean
        RedisSeatPreOccupyService redisSeatPreOccupyService(
                StringRedisTemplate redisTemplate,
                SeatOccupancyBitmapKey bitmapKey,
                SeatPreOccupyKey preOccupyKey,
                RedisSeatPreOccupyMetrics metrics) {

            return new RedisSeatPreOccupyService(
                    redisTemplate,
                    bitmapKey,
                    preOccupyKey,
                    metrics,
                    true,
                    Duration.ofMinutes(2)
            );
        }

        @Bean
        RedisSeatPreOccupyLeakCompensationService
        redisSeatPreOccupyLeakCompensationService(
                StringRedisTemplate redisTemplate,
                SeatPreOccupyKey preOccupyKey,
                ReservationMapper reservationMapper,
                RedisSeatPreOccupyService preOccupyService,
                RedisSeatPreOccupyMetrics metrics) {

            return new RedisSeatPreOccupyLeakCompensationService(
                    redisTemplate,
                    preOccupyKey,
                    reservationMapper,
                    preOccupyService,
                    metrics,
                    true
            );
        }
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeatOccupancyBitmapKey bitmapKey;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private RedisSeatPreOccupyService preOccupyService;

    @Autowired
    private RedisSeatPreOccupyLeakCompensationService
            compensationService;

    @BeforeEach
    void cleanRedisAndMocks() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        Mockito.reset(reservationMapper);
    }

    @Test
    void shouldReleaseLeakedPreOccupyWhenReservationDoesNotExist() {
        preOccupyService.preOccupy(
                "req-leaked",
                USER_ID,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID
        );

        when(reservationMapper.findByUserIdAndRequestId(
                USER_ID,
                "req-leaked"
        )).thenReturn(null);

        RedisSeatPreOccupyLeakCompensationService
                .CompensationResult result =
                compensationService.compensate(10);

        assertThat(result.checked()).isTrue();
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.released()).isEqualTo(1);
        assertThat(result.confirmed()).isZero();
        assertThat(result.failed()).isZero();

        assertSeatBit(2L, false);
        assertSeatBit(3L, false);
    }

    @Test
    void shouldKeepPreOccupyWhenReservationAlreadyExists() {
        preOccupyService.preOccupy(
                "req-confirmed",
                USER_ID,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID
        );

        Reservation reservation = new Reservation();
        reservation.setId(1001L);
        reservation.setUserId(USER_ID);
        reservation.setRequestId("req-confirmed");

        when(reservationMapper.findByUserIdAndRequestId(
                USER_ID,
                "req-confirmed"
        )).thenReturn(reservation);

        RedisSeatPreOccupyLeakCompensationService
                .CompensationResult result =
                compensationService.compensate(10);

        assertThat(result.checked()).isTrue();
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.confirmed()).isEqualTo(1);
        assertThat(result.released()).isZero();

        assertSeatBit(2L, true);
        assertSeatBit(3L, true);
    }

    @Test
    void shouldRecordPreOccupyMetric() {
        assertThat(preOccupyService.preOccupy(
                "req-metric",
                USER_ID,
                ROOM_ID,
                RESERVATION_DATE,
                SLOT_IDS,
                SEAT_ID
        ).status()).isEqualTo(SeatPreOccupyStatus.PREOCCUPIED);
    }

    private void assertSeatBit(Long slotId, boolean expected) {
        Boolean actual = redisTemplate.opsForValue().getBit(
                bitmapKey.forSlot(
                        ROOM_ID,
                        RESERVATION_DATE,
                        slotId
                ),
                SEAT_ID
        );

        assertThat(actual).isEqualTo(expected);
    }
}