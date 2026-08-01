package com.smartstudy.studyroom;

import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapConsistencyService;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapRebuildService;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSeatOccupancyBitmapConsistencyServiceTest {

    @Test
    void shouldReportConsistentWhenRedisMatchesMysql() {
        Fixture fixture = new Fixture();
        LocalDate date = LocalDate.of(2026, 8, 1);

        when(fixture.seatMapper.findByRoomId(1L))
                .thenReturn(List.of(seat(1001L), seat(1002L)));

        when(fixture.occupancyMapper.findByRoomDateAndSlotIds(
                1L,
                date,
                List.of(2L, 3L)
        )).thenReturn(List.of(occupancy(1001L, 2L)));

        when(fixture.bitmapService.findOccupiedSeatIds(
                1L,
                date,
                List.of(2L, 3L),
                setOf(1001L, 1002L)
        )).thenReturn(Optional.of(setOf(1001L)));

        RedisSeatOccupancyBitmapConsistencyService.ReconcileResult result =
                fixture.service.reconcile(
                        1L,
                        date,
                        List.of(2L, 3L)
                );

        assertThat(result.checked()).isTrue();
        assertThat(result.consistent()).isTrue();
        assertThat(result.rebuilt()).isFalse();
        verify(fixture.rebuildService, never()).rebuild(
                1L,
                date,
                List.of(2L, 3L)
        );
    }

    @Test
    void shouldRebuildWhenRedisProjectionIsMissing() {
        Fixture fixture = new Fixture();
        LocalDate date = LocalDate.of(2026, 8, 1);

        when(fixture.seatMapper.findByRoomId(1L))
                .thenReturn(List.of(seat(1001L), seat(1002L)));

        when(fixture.occupancyMapper.findByRoomDateAndSlotIds(
                1L,
                date,
                List.of(2L, 3L)
        )).thenReturn(List.of(occupancy(1001L, 2L)));

        when(fixture.bitmapService.findOccupiedSeatIds(
                1L,
                date,
                List.of(2L, 3L),
                setOf(1001L, 1002L)
        )).thenReturn(Optional.empty());

        when(fixture.rebuildService.rebuild(
                1L,
                date,
                List.of(2L, 3L)
        )).thenReturn(true);

        RedisSeatOccupancyBitmapConsistencyService.ReconcileResult result =
                fixture.service.reconcile(
                        1L,
                        date,
                        List.of(2L, 3L)
                );

        assertThat(result.checked()).isTrue();
        assertThat(result.consistent()).isFalse();
        assertThat(result.rebuilt()).isTrue();
        assertThat(result.reason())
                .isEqualTo("redis projection missing");
    }

    @Test
    void shouldRebuildWhenRedisProjectionIsInconsistent() {
        Fixture fixture = new Fixture();
        LocalDate date = LocalDate.of(2026, 8, 1);

        when(fixture.seatMapper.findByRoomId(1L))
                .thenReturn(List.of(seat(1001L), seat(1002L)));

        when(fixture.occupancyMapper.findByRoomDateAndSlotIds(
                1L,
                date,
                List.of(2L, 3L)
        )).thenReturn(List.of(occupancy(1001L, 2L)));

        when(fixture.bitmapService.findOccupiedSeatIds(
                1L,
                date,
                List.of(2L, 3L),
                setOf(1001L, 1002L)
        )).thenReturn(Optional.of(setOf(1002L)));

        when(fixture.rebuildService.rebuild(
                1L,
                date,
                List.of(2L, 3L)
        )).thenReturn(true);

        RedisSeatOccupancyBitmapConsistencyService.ReconcileResult result =
                fixture.service.reconcile(
                        1L,
                        date,
                        List.of(2L, 3L)
                );

        assertThat(result.checked()).isTrue();
        assertThat(result.consistent()).isFalse();
        assertThat(result.rebuilt()).isTrue();
        assertThat(result.mysqlOccupiedSeatIds())
                .isEqualTo(setOf(1001L));
        assertThat(result.redisOccupiedSeatIds())
                .isEqualTo(setOf(1002L));
    }

    @Test
    void shouldSkipWhenInputIsInvalid() {
        Fixture fixture = new Fixture();

        RedisSeatOccupancyBitmapConsistencyService.ReconcileResult result =
                fixture.service.reconcile(
                        null,
                        LocalDate.of(2026, 8, 1),
                        List.of(2L)
                );

        assertThat(result.checked()).isFalse();
        assertThat(result.reason()).isEqualTo("invalid input");
    }

    private static Seat seat(Long seatId) {
        Seat seat = new Seat();
        seat.setId(seatId);
        seat.setRoomId(1L);
        return seat;
    }

    private static ReservationSlotOccupancy occupancy(
            Long seatId,
            Long slotId) {

        ReservationSlotOccupancy occupancy =
                new ReservationSlotOccupancy();
        occupancy.setRoomId(1L);
        occupancy.setReservationDate(LocalDate.of(2026, 8, 1));
        occupancy.setSeatId(seatId);
        occupancy.setSlotId(slotId);
        return occupancy;
    }

    private static Set<Long> setOf(Long... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private static class Fixture {

        private final SeatMapper seatMapper =
                mock(SeatMapper.class);

        private final ReservationSlotOccupancyMapper occupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);

        private final SeatOccupancyBitmapService bitmapService =
                mock(SeatOccupancyBitmapService.class);

        private final RedisSeatOccupancyBitmapRebuildService rebuildService =
                mock(RedisSeatOccupancyBitmapRebuildService.class);

        private final RedisSeatOccupancyBitmapConsistencyService service =
                new RedisSeatOccupancyBitmapConsistencyService(
                        seatMapper,
                        occupancyMapper,
                        bitmapService,
                        rebuildService,
                        true
                );
    }
}