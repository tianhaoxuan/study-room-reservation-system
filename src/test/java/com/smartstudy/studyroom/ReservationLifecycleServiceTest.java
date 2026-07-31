package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.service.ReservationLifecycleService;
import com.smartstudy.studyroom.service.RoomStatsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

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

        when(fixture.reservationMapper.updateStatusIfCurrentIn(
                reservation.getId(),
                java.util.List.of(
                        BizConstants.RESERVATION_PENDING,
                        BizConstants.RESERVATION_USING
                ),
                BizConstants.RESERVATION_CANCELED
        )).thenReturn(1);

        fixture.service.cancelByAdmin(reservation);

        verify(fixture.occupancyMapper)
                .deleteByReservationId(reservation.getId());
        verify(fixture.roomStatsService)
                .refreshRoomSeatStats(reservation.getRoomId());
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
                        java.time.LocalDateTime.now()
                )
        );

        assertThat(exception.getCode()).isEqualTo(StatusCode.PARAM_ERROR);
        verify(fixture.occupancyMapper, never())
                .deleteByReservationId(reservation.getId());
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

    private static class Fixture {

        private final ReservationMapper reservationMapper =
                mock(ReservationMapper.class);

        private final ReservationSlotOccupancyMapper occupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);

        private final RoomStatsService roomStatsService =
                mock(RoomStatsService.class);

        private final ReservationLifecycleService service =
                new ReservationLifecycleService(
                        reservationMapper,
                        occupancyMapper,
                        roomStatsService
                );
    }
}
