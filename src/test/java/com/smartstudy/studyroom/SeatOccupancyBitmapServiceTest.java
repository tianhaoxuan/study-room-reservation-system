package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.SeatOccupancyBitmapKey;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatOccupancyBitmapServiceTest {

    @Test
    void shouldMarkSeatOccupiedAndSetTtl() {
        StringRedisTemplate redisTemplate =
                mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations =
                mockValueOperations();

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        SeatOccupancyBitmapService service =
                new SeatOccupancyBitmapService(
                        redisTemplate,
                        new SeatOccupancyBitmapKey("seat:bitmap"),
                        45
                );

        service.occupy(
                1L,
                LocalDate.of(2026, 8, 1),
                2L,
                1001L
        );

        String key = "seat:bitmap:1:2026-08-01:2";

        verify(valueOperations).setBit(
                key,
                1001L,
                true
        );
        verify(redisTemplate).expire(
                key,
                Duration.ofDays(45)
        );
    }

    @Test
    void shouldReleaseSeatOccupancyAndKeepTtl() {
        StringRedisTemplate redisTemplate =
                mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations =
                mockValueOperations();

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        SeatOccupancyBitmapService service =
                new SeatOccupancyBitmapService(
                        redisTemplate,
                        new SeatOccupancyBitmapKey("seat:bitmap"),
                        45
                );

        service.release(
                1L,
                LocalDate.of(2026, 8, 1),
                2L,
                1001L
        );

        String key = "seat:bitmap:1:2026-08-01:2";

        verify(valueOperations).setBit(
                key,
                1001L,
                false
        );
        verify(redisTemplate).expire(
                key,
                Duration.ofDays(45)
        );
    }

    @Test
    void shouldRebuildSlotBitmapAndCreateKeyWhenSlotHasNoOccupiedSeats() {
        StringRedisTemplate redisTemplate =
                mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations =
                mockValueOperations();

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        SeatOccupancyBitmapService service =
                new SeatOccupancyBitmapService(
                        redisTemplate,
                        new SeatOccupancyBitmapKey("seat:bitmap"),
                        45
                );

        boolean rebuilt =
                service.rebuildSlot(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        2L,
                        List.of()
                );

        assertThat(rebuilt).isTrue();

        String key = "seat:bitmap:1:2026-08-01:2";

        verify(redisTemplate).delete(key);
        verify(valueOperations).setBit(
                key,
                0L,
                false
        );
        verify(redisTemplate).expire(
                key,
                Duration.ofDays(45)
        );
    }

    @Test
    void shouldFindSeatsOccupiedInAnySelectedSlot() {
        StringRedisTemplate redisTemplate =
                mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations =
                mockValueOperations();

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(redisTemplate.hasKey("seat:bitmap:1:2026-08-01:2"))
                .thenReturn(true);
        when(redisTemplate.hasKey("seat:bitmap:1:2026-08-01:3"))
                .thenReturn(true);

        when(valueOperations.getBit(
                "seat:bitmap:1:2026-08-01:2",
                1001L
        )).thenReturn(false);
        when(valueOperations.getBit(
                "seat:bitmap:1:2026-08-01:3",
                1001L
        )).thenReturn(true);
        when(valueOperations.getBit(
                "seat:bitmap:1:2026-08-01:2",
                1002L
        )).thenReturn(false);
        when(valueOperations.getBit(
                "seat:bitmap:1:2026-08-01:3",
                1002L
        )).thenReturn(false);

        SeatOccupancyBitmapService service =
                new SeatOccupancyBitmapService(
                        redisTemplate,
                        new SeatOccupancyBitmapKey("seat:bitmap"),
                        45
                );

        Optional<Set<Long>> occupiedSeatIds =
                service.findOccupiedSeatIds(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of(2L, 3L),
                        List.of(1001L, 1002L)
                );

        assertThat(occupiedSeatIds)
                .contains(setOf(1001L));
    }

    @Test
    void shouldReturnEmptyOptionalWhenAnySlotBitmapKeyIsMissing() {
        StringRedisTemplate redisTemplate =
                mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations =
                mockValueOperations();

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(redisTemplate.hasKey("seat:bitmap:1:2026-08-01:2"))
                .thenReturn(true);
        when(redisTemplate.hasKey("seat:bitmap:1:2026-08-01:3"))
                .thenReturn(false);

        SeatOccupancyBitmapService service =
                new SeatOccupancyBitmapService(
                        redisTemplate,
                        new SeatOccupancyBitmapKey("seat:bitmap"),
                        45
                );

        Optional<Set<Long>> occupiedSeatIds =
                service.findOccupiedSeatIds(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of(2L, 3L),
                        List.of(1001L)
                );

        assertThat(occupiedSeatIds)
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyOptionalWhenRedisReadFails() {
        StringRedisTemplate redisTemplate =
                mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations =
                mockValueOperations();

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(redisTemplate.hasKey("seat:bitmap:1:2026-08-01:2"))
                .thenThrow(new IllegalStateException("redis down"));

        SeatOccupancyBitmapService service =
                new SeatOccupancyBitmapService(
                        redisTemplate,
                        new SeatOccupancyBitmapKey("seat:bitmap"),
                        45
                );

        Optional<Set<Long>> occupiedSeatIds =
                service.findOccupiedSeatIds(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of(2L, 3L),
                        List.of(1001L)
                );

        assertThat(occupiedSeatIds)
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValueOperations() {
        return mock(ValueOperations.class);
    }

    private static Set<Long> setOf(Long... values) {
        return new LinkedHashSet<>(List.of(values));
    }
}