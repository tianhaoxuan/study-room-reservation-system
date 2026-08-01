package com.smartstudy.studyroom;

import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapRebuildLockService;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapRebuildService;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisSeatOccupancyBitmapRebuildServiceTest {

    @Test
    void shouldRebuildEachRequestedSlotFromMysqlOccupancies() {
        ReservationSlotOccupancyMapper occupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);
        SeatOccupancyBitmapService bitmapService =
                mock(SeatOccupancyBitmapService.class);
        RedisSeatOccupancyBitmapRebuildLockService lockService =
                mock(RedisSeatOccupancyBitmapRebuildLockService.class);
        RedisSeatOccupancyBitmapRebuildService rebuildService =
                new RedisSeatOccupancyBitmapRebuildService(
                        occupancyMapper,
                        bitmapService,
                        lockService
                );

        LocalDate reservationDate =
                LocalDate.of(2026, 8, 1);
        List<Long> slotIds =
                List.of(2L, 3L, 4L);

        when(lockService.runWithLock(
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            Supplier<Boolean> action =
                    invocation.getArgument(3);
            return action.get();
        });

        when(occupancyMapper.findByRoomDateAndSlotIds(
                1L,
                reservationDate,
                slotIds
        )).thenReturn(List.of(
                occupancy(1001L, 2L),
                occupancy(1002L, 2L),
                occupancy(1003L, 4L)
        ));

        when(bitmapService.rebuildSlot(
                1L,
                reservationDate,
                2L,
                setOf(1001L, 1002L)
        )).thenReturn(true);
        when(bitmapService.rebuildSlot(
                1L,
                reservationDate,
                3L,
                setOf()
        )).thenReturn(true);
        when(bitmapService.rebuildSlot(
                1L,
                reservationDate,
                4L,
                setOf(1003L)
        )).thenReturn(true);

        boolean rebuilt =
                rebuildService.rebuild(
                        1L,
                        reservationDate,
                        slotIds
                );

        assertThat(rebuilt).isTrue();

        verify(lockService).runWithLock(
                any(),
                any(),
                any(),
                any()
        );
        verify(bitmapService).rebuildSlot(
                1L,
                reservationDate,
                2L,
                setOf(1001L, 1002L)
        );
        verify(bitmapService).rebuildSlot(
                1L,
                reservationDate,
                3L,
                setOf()
        );
        verify(bitmapService).rebuildSlot(
                1L,
                reservationDate,
                4L,
                setOf(1003L)
        );
    }

    @Test
    void shouldReturnFalseWhenAnySlotRebuildFails() {
        ReservationSlotOccupancyMapper occupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);
        SeatOccupancyBitmapService bitmapService =
                mock(SeatOccupancyBitmapService.class);
        RedisSeatOccupancyBitmapRebuildLockService lockService =
                mock(RedisSeatOccupancyBitmapRebuildLockService.class);
        RedisSeatOccupancyBitmapRebuildService rebuildService =
                new RedisSeatOccupancyBitmapRebuildService(
                        occupancyMapper,
                        bitmapService,
                        lockService
                );

        LocalDate reservationDate =
                LocalDate.of(2026, 8, 1);
        List<Long> slotIds =
                List.of(2L, 3L);

        when(lockService.runWithLock(
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            Supplier<Boolean> action =
                    invocation.getArgument(3);
            return action.get();
        });

        when(occupancyMapper.findByRoomDateAndSlotIds(
                1L,
                reservationDate,
                slotIds
        )).thenReturn(List.of(
                occupancy(1001L, 2L)
        ));

        when(bitmapService.rebuildSlot(
                1L,
                reservationDate,
                2L,
                setOf(1001L)
        )).thenReturn(true);
        when(bitmapService.rebuildSlot(
                1L,
                reservationDate,
                3L,
                setOf()
        )).thenReturn(false);

        boolean rebuilt =
                rebuildService.rebuild(
                        1L,
                        reservationDate,
                        slotIds
                );

        assertThat(rebuilt).isFalse();
    }

    @Test
    void shouldReturnFalseWhenRebuildLockIsNotAcquired() {
        ReservationSlotOccupancyMapper occupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);
        SeatOccupancyBitmapService bitmapService =
                mock(SeatOccupancyBitmapService.class);
        RedisSeatOccupancyBitmapRebuildLockService lockService =
                mock(RedisSeatOccupancyBitmapRebuildLockService.class);
        RedisSeatOccupancyBitmapRebuildService rebuildService =
                new RedisSeatOccupancyBitmapRebuildService(
                        occupancyMapper,
                        bitmapService,
                        lockService
                );

        LocalDate reservationDate =
                LocalDate.of(2026, 8, 1);
        List<Long> slotIds =
                List.of(2L, 3L);

        when(lockService.runWithLock(
                eq(1L),
                eq(reservationDate),
                eq(slotIds),
                any()
        )).thenReturn(false);

        boolean rebuilt =
                rebuildService.rebuild(
                        1L,
                        reservationDate,
                        slotIds
                );

        assertThat(rebuilt).isFalse();
        verifyNoInteractions(occupancyMapper, bitmapService);
    }

    @Test
    void shouldReturnFalseWhenInputIsInvalid() {
        ReservationSlotOccupancyMapper occupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);
        SeatOccupancyBitmapService bitmapService =
                mock(SeatOccupancyBitmapService.class);
        RedisSeatOccupancyBitmapRebuildLockService lockService =
                mock(RedisSeatOccupancyBitmapRebuildLockService.class);
        RedisSeatOccupancyBitmapRebuildService rebuildService =
                new RedisSeatOccupancyBitmapRebuildService(
                        occupancyMapper,
                        bitmapService,
                        lockService
                );

        boolean rebuilt =
                rebuildService.rebuild(
                        1L,
                        LocalDate.of(2026, 8, 1),
                        List.of()
                );

        assertThat(rebuilt).isFalse();
        verifyNoInteractions(lockService);
    }

    private static ReservationSlotOccupancy occupancy(
            Long seatId,
            Long slotId) {

        ReservationSlotOccupancy occupancy =
                new ReservationSlotOccupancy();
        occupancy.setSeatId(seatId);
        occupancy.setSlotId(slotId);
        return occupancy;
    }

    private static Set<Long> setOf(Long... values) {
        return new LinkedHashSet<>(List.of(values));
    }
}