package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.UserMapper;
import com.smartstudy.studyroom.mapper.ViolationMapper;
import com.smartstudy.studyroom.redis.ReservationSeatBitmapProjectionService;
import com.smartstudy.studyroom.service.ConfigService;
import com.smartstudy.studyroom.service.ReservationLifecycleService;
import com.smartstudy.studyroom.service.ReservationTimeoutService;
import com.smartstudy.studyroom.service.RoomStatsService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationTimeoutMessageServiceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZONE).toInstant(),
            ZONE
    );

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
                        NOW.minusMinutes(1)
                );

        assertThat(handled).isFalse();
        verify(fixture.violationMapper, never()).insert(any());
        verify(fixture.occupancyMapper, never())
                .deleteByReservationId(1001L);
        verify(fixture.bitmapProjectionService, never())
                .projectReleasedAfterCommit(any());
    }

    @Test
    void shouldHandlePendingReservationAfterDeadline() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_PENDING
        );
        List<ReservationSlotOccupancy> occupancies =
                List.of(occupancy(2L), occupancy(3L));

        when(fixture.reservationMapper.findById(1001L))
                .thenReturn(reservation);
        when(fixture.reservationMapper.updateStatusIfCurrent(
                1001L,
                BizConstants.RESERVATION_PENDING,
                BizConstants.RESERVATION_VIOLATED
        )).thenReturn(1);
        when(fixture.occupancyMapper.findByReservationId(1001L))
                .thenReturn(occupancies);
        when(fixture.configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        )).thenReturn(3);

        boolean handled =
                fixture.service.handleCheckinTimeoutMessage(
                        1001L,
                        NOW.minusMinutes(1)
                );

        assertThat(handled).isTrue();
        verify(fixture.violationMapper).insert(any());
        verify(fixture.occupancyMapper).deleteByReservationId(1001L);
        verify(fixture.bitmapProjectionService)
                .projectReleasedAfterCommit(occupancies);
    }

    @Test
    void shouldIgnoreRepeatedTimeoutMessageWhenStateAlreadyChanged() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_PENDING
        );

        when(fixture.reservationMapper.findById(1001L))
                .thenReturn(reservation);
        when(fixture.configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        )).thenReturn(3);
        when(fixture.reservationMapper.updateStatusIfCurrent(
                1001L,
                BizConstants.RESERVATION_PENDING,
                BizConstants.RESERVATION_VIOLATED
        )).thenReturn(0);

        boolean handled =
                fixture.service.handleCheckinTimeoutMessage(
                        1001L,
                        NOW.minusMinutes(1)
                );

        assertThat(handled).isFalse();
        verify(fixture.violationMapper, never()).insert(any());
        verify(fixture.occupancyMapper, never())
                .deleteByReservationId(1001L);
        verify(fixture.bitmapProjectionService, never())
                .projectReleasedAfterCommit(any());
    }

    @Test
    void shouldCompensateExpiredPendingReservationsByWindow() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_PENDING
        );
        List<ReservationSlotOccupancy> occupancies =
                List.of(occupancy(2L), occupancy(3L));

        when(fixture.configService.getIntConfig(
                BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES,
                15
        )).thenReturn(15);
        when(fixture.configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        )).thenReturn(3);
        when(fixture.reservationMapper.findPendingCheckinExpiredWithin(
                BizConstants.RESERVATION_PENDING,
                NOW.minusHours(24),
                NOW.minusMinutes(15),
                50
        )).thenReturn(List.of(reservation));
        when(fixture.reservationMapper.findInUseEndedWithin(
                BizConstants.RESERVATION_USING,
                NOW.minusHours(24),
                NOW,
                50
        )).thenReturn(List.of());
        when(fixture.reservationMapper.updateStatusIfCurrent(
                1001L,
                BizConstants.RESERVATION_PENDING,
                BizConstants.RESERVATION_VIOLATED
        )).thenReturn(1);
        when(fixture.occupancyMapper.findByReservationId(1001L))
                .thenReturn(occupancies);

        int handled = fixture.service.compensateExpiredReservations(
                50,
                Duration.ofHours(24)
        );

        assertThat(handled).isEqualTo(1);
        verify(fixture.violationMapper).insert(any());
        verify(fixture.occupancyMapper).deleteByReservationId(1001L);
        verify(fixture.bitmapProjectionService)
                .projectReleasedAfterCommit(occupancies);
        verify(fixture.reservationMapper, never()).findByStatus(any());
    }

    @Test
    void shouldCompleteExpiredInUseReservationsDuringCompensationScan() {
        Fixture fixture = new Fixture();
        Reservation reservation = reservation(
                BizConstants.RESERVATION_USING
        );
        reservation.setReservationDate(LocalDate.of(2026, 8, 1));
        reservation.setEndTime(LocalTime.of(8, 30));

        List<ReservationSlotOccupancy> occupancies =
                List.of(occupancy(2L), occupancy(3L));

        when(fixture.configService.getIntConfig(
                BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES,
                15
        )).thenReturn(15);
        when(fixture.configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        )).thenReturn(3);
        when(fixture.reservationMapper.findPendingCheckinExpiredWithin(
                BizConstants.RESERVATION_PENDING,
                NOW.minusHours(24),
                NOW.minusMinutes(15),
                50
        )).thenReturn(List.of());
        when(fixture.reservationMapper.findInUseEndedWithin(
                BizConstants.RESERVATION_USING,
                NOW.minusHours(24),
                NOW,
                50
        )).thenReturn(List.of(reservation));
        when(fixture.reservationMapper.markLeft(
                eq(1001L),
                eq(BizConstants.RESERVATION_USING),
                eq(BizConstants.RESERVATION_FINISHED),
                any()
        )).thenReturn(1);
        when(fixture.occupancyMapper.findByReservationId(1001L))
                .thenReturn(occupancies);

        int handled = fixture.service.compensateExpiredReservations(
                50,
                Duration.ofHours(24)
        );

        assertThat(handled).isEqualTo(1);
        verify(fixture.occupancyMapper)
                .deleteByReservationId(1001L);
        verify(fixture.bitmapProjectionService)
                .projectReleasedAfterCommit(occupancies);
        verify(fixture.violationMapper, never()).insert(any());
        verify(fixture.reservationMapper, never()).findByStatus(any());
    }

    private static Reservation reservation(int status) {
        Reservation reservation = new Reservation();
        reservation.setId(1001L);
        reservation.setUserId(1L);
        reservation.setSeatId(2L);
        reservation.setRoomId(3L);
        reservation.setReservationDate(LocalDate.of(2026, 8, 1));
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
        occupancy.setReservationDate(LocalDate.of(2026, 8, 1));
        occupancy.setSlotId(slotId);
        return occupancy;
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

        private final ReservationSeatBitmapProjectionService
                bitmapProjectionService =
                mock(ReservationSeatBitmapProjectionService.class);

        private final ReservationLifecycleService lifecycleService =
                new ReservationLifecycleService(
                        reservationMapper,
                        occupancyMapper,
                        roomStatsService,
                        bitmapProjectionService
                );

        private final ReservationTimeoutService service =
                new ReservationTimeoutService(
                        reservationMapper,
                        lifecycleService,
                        violationMapper,
                        userMapper,
                        configService,
                        CLOCK
                );
    }
}