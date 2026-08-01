package com.smartstudy.studyroom;

import com.smartstudy.studyroom.mapper.ReservationSlotMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapBatchConsistencyService;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapConsistencyService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSeatOccupancyBitmapBatchConsistencyServiceTest {

    @Test
    void shouldReconcileEachActiveRoomForEachDate() {
        Fixture fixture = new Fixture();
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        List<Long> slotIds = List.of(2L, 3L);

        when(fixture.studyRoomMapper.findActiveRoomIds())
                .thenReturn(List.of(1L, 2L));
        when(fixture.reservationSlotMapper.findEnabledSlotIds())
                .thenReturn(slotIds);

        when(fixture.consistencyService.reconcile(
                eq(1L),
                eq(startDate),
                eq(slotIds)
        )).thenReturn(consistent());

        when(fixture.consistencyService.reconcile(
                eq(2L),
                eq(startDate),
                eq(slotIds)
        )).thenReturn(rebuilt());

        when(fixture.consistencyService.reconcile(
                eq(1L),
                eq(startDate.plusDays(1)),
                eq(slotIds)
        )).thenReturn(consistent());

        when(fixture.consistencyService.reconcile(
                eq(2L),
                eq(startDate.plusDays(1)),
                eq(slotIds)
        )).thenReturn(consistent());

        RedisSeatOccupancyBitmapBatchConsistencyService.BatchReconcileResult
                result = fixture.service.reconcileFrom(
                startDate,
                2,
                50
        );

        assertThat(result.rooms()).isEqualTo(2);
        assertThat(result.dates()).isEqualTo(2);
        assertThat(result.checked()).isEqualTo(4);
        assertThat(result.consistent()).isEqualTo(3);
        assertThat(result.rebuilt()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void shouldLimitRoomCount() {
        Fixture fixture = new Fixture();
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        List<Long> slotIds = List.of(2L);

        when(fixture.studyRoomMapper.findActiveRoomIds())
                .thenReturn(List.of(1L, 2L, 3L));
        when(fixture.reservationSlotMapper.findEnabledSlotIds())
                .thenReturn(slotIds);

        when(fixture.consistencyService.reconcile(
                eq(1L),
                eq(startDate),
                eq(slotIds)
        )).thenReturn(consistent());

        when(fixture.consistencyService.reconcile(
                eq(2L),
                eq(startDate),
                eq(slotIds)
        )).thenReturn(consistent());

        RedisSeatOccupancyBitmapBatchConsistencyService.BatchReconcileResult
                result = fixture.service.reconcileFrom(
                startDate,
                1,
                2
        );

        assertThat(result.rooms()).isEqualTo(2);
        assertThat(result.checked()).isEqualTo(2);

        verify(fixture.consistencyService, never()).reconcile(
                eq(3L),
                eq(startDate),
                eq(slotIds)
        );
    }

    @Test
    void shouldSkipWhenNoActiveRooms() {
        Fixture fixture = new Fixture();

        when(fixture.studyRoomMapper.findActiveRoomIds())
                .thenReturn(List.of());
        when(fixture.reservationSlotMapper.findEnabledSlotIds())
                .thenReturn(List.of(2L));

        RedisSeatOccupancyBitmapBatchConsistencyService.BatchReconcileResult
                result = fixture.service.reconcileFrom(
                LocalDate.of(2026, 8, 1),
                2,
                50
        );

        assertThat(result.checked()).isZero();
        assertThat(result.reason()).isEqualTo("no active rooms");
    }

    @Test
    void shouldSkipWhenNoEnabledSlots() {
        Fixture fixture = new Fixture();

        when(fixture.studyRoomMapper.findActiveRoomIds())
                .thenReturn(List.of(1L));
        when(fixture.reservationSlotMapper.findEnabledSlotIds())
                .thenReturn(List.of());

        RedisSeatOccupancyBitmapBatchConsistencyService.BatchReconcileResult
                result = fixture.service.reconcileFrom(
                LocalDate.of(2026, 8, 1),
                2,
                50
        );

        assertThat(result.checked()).isZero();
        assertThat(result.reason()).isEqualTo("no enabled slots");
    }

    private static RedisSeatOccupancyBitmapConsistencyService.ReconcileResult
    consistent() {

        return RedisSeatOccupancyBitmapConsistencyService
                .ReconcileResult
                .consistent(Set.of(), Set.of());
    }

    private static RedisSeatOccupancyBitmapConsistencyService.ReconcileResult
    rebuilt() {

        return RedisSeatOccupancyBitmapConsistencyService
                .ReconcileResult
                .rebuilt(
                        "redis projection inconsistent",
                        true,
                        Set.of(1001L),
                        Set.of(1002L)
                );
    }

    private static class Fixture {

        private final StudyRoomMapper studyRoomMapper =
                mock(StudyRoomMapper.class);

        private final ReservationSlotMapper reservationSlotMapper =
                mock(ReservationSlotMapper.class);

        private final RedisSeatOccupancyBitmapConsistencyService
                consistencyService =
                mock(RedisSeatOccupancyBitmapConsistencyService.class);

        private final RedisSeatOccupancyBitmapBatchConsistencyService service =
                new RedisSeatOccupancyBitmapBatchConsistencyService(
                        studyRoomMapper,
                        reservationSlotMapper,
                        consistencyService
                );
    }
}