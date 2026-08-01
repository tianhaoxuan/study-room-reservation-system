package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.redis.ReservationSeatBitmapProjectionService;
import com.smartstudy.studyroom.service.ReservationLifecycleService;
import com.smartstudy.studyroom.service.RoomStatsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationLifecycleServiceTest {

    @Test
    void shouldCancelInUseReservationByAdminAndReleaseOccupancy() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_USING
        );
        List<ReservationSlotOccupancy> occupancies =
                List.of(occupancy(2L), occupancy(3L));

        when(fixture.reservationMapper.updateStatusIfCurrentIn(
                reservation.getId(),
                java.util.List.of(
                        BizConstants.RESERVATION_PENDING,
                        BizConstants.RESERVATION_USING
                ),
                BizConstants.RESERVATION_CANCELED
        )).thenReturn(1);

        when(fixture.occupancyMapper.findByReservationId(
                reservation.getId()
        )).thenReturn(occupancies);

        fixture.service.cancelByAdmin(reservation);

        verify(fixture.occupancyMapper)
                .deleteByReservationId(reservation.getId());
        verify(fixture.bitmapProjectionService)
                .projectReleasedAfterCommit(occupancies);
        verify(fixture.roomStatsService)
                .refreshRoomSeatStats(reservation.getRoomId());
    }

    @Test
    void shouldCompleteExpiredInUseReservationAndReleaseOccupancy() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_USING
        );
        LocalDateTime completeTime =
                LocalDateTime.of(2026, 8, 1, 10, 1);
        List<ReservationSlotOccupancy> occupancies =
                List.of(occupancy(2L), occupancy(3L));

        when(fixture.reservationMapper.markLeft(
                reservation.getId(),
                BizConstants.RESERVATION_USING,
                BizConstants.RESERVATION_FINISHED,
                completeTime
        )).thenReturn(1);

        when(fixture.occupancyMapper.findByReservationId(
                reservation.getId()
        )).thenReturn(occupancies);

        boolean changed =
                fixture.service.completeExpiredInUse(
                        reservation,
                        completeTime
                );

        assertThat(changed).isTrue();
        verify(fixture.occupancyMapper)
                .deleteByReservationId(reservation.getId());
        verify(fixture.bitmapProjectionService)
                .projectReleasedAfterCommit(occupancies);
        verify(fixture.roomStatsService)
                .refreshRoomSeatStats(reservation.getRoomId());
    }

    @Test
    void shouldReturnFalseWhenExpiredCompletionStateAlreadyChanged() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_USING
        );
        LocalDateTime completeTime =
                LocalDateTime.of(2026, 8, 1, 10, 1);

        when(fixture.reservationMapper.markLeft(
                reservation.getId(),
                BizConstants.RESERVATION_USING,
                BizConstants.RESERVATION_FINISHED,
                completeTime
        )).thenReturn(0);

        boolean changed =
                fixture.service.completeExpiredInUse(
                        reservation,
                        completeTime
                );

        assertThat(changed).isFalse();
        verify(fixture.occupancyMapper, never())
                .deleteByReservationId(reservation.getId());
        verify(fixture.bitmapProjectionService, never())
                .projectReleasedAfterCommit(java.util.List.of());
        verify(fixture.roomStatsService, never())
                .refreshRoomSeatStats(reservation.getRoomId());
    }

    @Test
    void shouldIgnoreExpiredCompletionWhenReservationIsNotInUse() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_PENDING
        );

        boolean changed =
                fixture.service.completeExpiredInUse(
                        reservation,
                        LocalDateTime.now()
                );

        assertThat(changed).isFalse();
        verify(fixture.occupancyMapper, never())
                .deleteByReservationId(reservation.getId());
        verify(fixture.bitmapProjectionService, never())
                .projectReleasedAfterCommit(java.util.List.of());
    }

    @Test
    void shouldNotReleaseOccupancyWhenNoShowStateAlreadyChanged() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_PENDING
        );

        when(fixture.reservationMapper.updateStatusIfCurrent(
                reservation.getId(),
                BizConstants.RESERVATION_PENDING,
                BizConstants.RESERVATION_VIOLATED
        )).thenReturn(0);

        boolean changed = fixture.service.violateNoShow(reservation);

        assertThat(changed).isFalse();
        verify(fixture.occupancyMapper, never())
                .deleteByReservationId(reservation.getId());
        verify(fixture.bitmapProjectionService, never())
                .projectReleasedAfterCommit(java.util.List.of());
        verify(fixture.roomStatsService, never())
                .refreshRoomSeatStats(reservation.getRoomId());
    }

    @Test
    void shouldRejectLeaveWhenReservationIsPendingCheckin() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_PENDING
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.completeByLeave(
                        reservation,
                        LocalDateTime.now()
                )
        );

        assertThat(exception.getCode()).isEqualTo(StatusCode.PARAM_ERROR);
        verify(fixture.occupancyMapper, never())
                .deleteByReservationId(reservation.getId());
        verify(fixture.bitmapProjectionService, never())
                .projectReleasedAfterCommit(java.util.List.of());
    }

    private static Reservation reservation(int status) {
        Reservation reservation = new Reservation();
        reservation.setId(1001L);
        reservation.setUserId(1L);
        reservation.setSeatId(2L);
        reservation.setRoomId(3L);
        reservation.setReservationDate(LocalDate.now());
        reservation.setStartTime(LocalTime.of(8, 0));
        reservation.setEndTime(LocalTime.of(10, 0));
        reservation.setStatus(status);
        return reservation;
    }

    private static ReservationSlotOccupancy occupancy(Long slotId) {
        ReservationSlotOccupancy occupancy =
                new ReservationSlotOccupancy();
        occupancy.setReservationId(1001L);
        occupancy.setUserId(1L);
        occupancy.setSeatId(2L);
        occupancy.setRoomId(3L);
        occupancy.setReservationDate(LocalDate.now());
        occupancy.setSlotId(slotId);
        return occupancy;
    }

    private static class Fixture {

        private final ReservationMapper reservationMapper =
                mock(ReservationMapper.class);

        private final ReservationSlotOccupancyMapper occupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);

        private final RoomStatsService roomStatsService =
                mock(RoomStatsService.class);

        private final ReservationSeatBitmapProjectionService
                bitmapProjectionService =
                mock(ReservationSeatBitmapProjectionService.class);

        private final ReservationLifecycleService service =
                new ReservationLifecycleService(
                        reservationMapper,
                        occupancyMapper,
                        roomStatsService,
                        bitmapProjectionService
                );
    }
}