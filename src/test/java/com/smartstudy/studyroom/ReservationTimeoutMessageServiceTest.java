package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.UserMapper;
import com.smartstudy.studyroom.mapper.ViolationMapper;
import com.smartstudy.studyroom.service.ConfigService;
import com.smartstudy.studyroom.service.ReservationLifecycleService;
import com.smartstudy.studyroom.service.ReservationTimeoutService;
import com.smartstudy.studyroom.service.RoomStatsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationTimeoutMessageServiceTest {

    @Test
    void shouldIgnoreMessageWhenReservationAlreadySigned() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_USING
        );

        when(fixture.reservationMapper.findById(1001L))
                .thenReturn(reservation);

        boolean handled =
                fixture.service.handleCheckinTimeoutMessage(
                        1001L,
                        LocalDateTime.now().minusMinutes(1)
                );

        assertThat(handled).isFalse();
        verify(fixture.violationMapper, never()).insert(any());
        verify(fixture.occupancyMapper, never())
                .deleteByReservationId(1001L);
    }

    @Test
    void shouldHandlePendingReservationAfterDeadline() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_PENDING
        );

        when(fixture.reservationMapper.findById(1001L))
                .thenReturn(reservation);
        when(fixture.reservationMapper.updateStatusIfCurrent(
                1001L,
                BizConstants.RESERVATION_PENDING,
                BizConstants.RESERVATION_VIOLATED
        )).thenReturn(1);
        when(fixture.configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        )).thenReturn(3);

        boolean handled =
                fixture.service.handleCheckinTimeoutMessage(
                        1001L,
                        LocalDateTime.now().minusMinutes(1)
                );

        assertThat(handled).isTrue();
        verify(fixture.violationMapper).insert(any());
        verify(fixture.occupancyMapper).deleteByReservationId(1001L);
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

        private final ViolationMapper violationMapper =
                mock(ViolationMapper.class);

        private final UserMapper userMapper =
                mock(UserMapper.class);

        private final ConfigService configService =
                mock(ConfigService.class);

        private final RoomStatsService roomStatsService =
                mock(RoomStatsService.class);

        private final ReservationLifecycleService lifecycleService =
                new ReservationLifecycleService(
                        reservationMapper,
                        occupancyMapper,
                        roomStatsService
                );

        private final ReservationTimeoutService service =
                new ReservationTimeoutService(
                        reservationMapper,
                        lifecycleService,
                        violationMapper,
                        userMapper,
                        configService
                );
    }
}
