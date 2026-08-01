package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.dto.ReservationSlotRange;
import com.smartstudy.studyroom.dto.SeatAvailabilityResponse;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.SeatAvailabilityMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapRebuildService;
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapService;
import com.smartstudy.studyroom.service.ReservationSlotService;
import com.smartstudy.studyroom.service.SeatAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SeatAvailabilityServiceTest {

    private SeatMapper seatMapper;
    private StudyRoomMapper studyRoomMapper;
    private SeatAvailabilityMapper seatAvailabilityMapper;
    private ReservationSlotService reservationSlotService;
    private SeatOccupancyBitmapService bitmapService;
    private RedisSeatOccupancyBitmapRebuildService bitmapRebuildService;
    private SeatAvailabilityService seatAvailabilityService;

    @BeforeEach
    void setUp() {
        seatMapper = mock(SeatMapper.class);
        studyRoomMapper = mock(StudyRoomMapper.class);
        seatAvailabilityMapper =
                mock(SeatAvailabilityMapper.class);
        reservationSlotService =
                mock(ReservationSlotService.class);
        bitmapService = mock(SeatOccupancyBitmapService.class);
        bitmapRebuildService =
                mock(RedisSeatOccupancyBitmapRebuildService.class);

        seatAvailabilityService = new SeatAvailabilityService(
                seatMapper,
                studyRoomMapper,
                seatAvailabilityMapper,
                reservationSlotService,
                bitmapService,
                bitmapRebuildService,
                true
        );
    }

    @Test
    void returnsReservedStatusesFromRedisBitmapWhenProjectionExists() {
        LocalDate reservationDate =
                LocalDate.now().plusDays(1);

        StudyRoom room = enabledRoom(1L);

        ReservationSlotRange slotRange =
                new ReservationSlotRange(
                        List.of(2L, 3L, 4L, 5L),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        "08:00-10:00"
                );

        Seat seat1 = seat(
                1L,
                BizConstants.SEAT_STATUS_FREE
        );
        Seat seat2 = seat(
                2L,
                BizConstants.SEAT_STATUS_FREE
        );
        Seat seat3 = seat(
                3L,
                BizConstants.SEAT_STATUS_REPAIR
        );

        when(studyRoomMapper.findById(1L))
                .thenReturn(room);

        when(reservationSlotService.resolveSelectableRange(
                room,
                2L,
                5L
        )).thenReturn(slotRange);

        when(seatMapper.findByRoomId(1L))
                .thenReturn(List.of(seat1, seat2, seat3));

        when(bitmapService.findOccupiedSeatIds(
                1L,
                reservationDate,
                List.of(2L, 3L, 4L, 5L),
                List.of(1L, 2L, 3L)
        )).thenReturn(Optional.of(Set.of(1L, 2L)));

        List<SeatAvailabilityResponse> result =
                seatAvailabilityService.findAvailableSeats(
                        1L,
                        reservationDate,
                        2L,
                        5L
                );

        assertThat(result).hasSize(3);

        assertThat(result.get(0).status())
                .isEqualTo(BizConstants.SEAT_STATUS_RESERVED);

        assertThat(result.get(1).status())
                .isEqualTo(BizConstants.SEAT_STATUS_RESERVED);

        assertThat(result.get(2).status())
                .isEqualTo(BizConstants.SEAT_STATUS_REPAIR);

        verify(bitmapRebuildService, never())
                .rebuild(1L, reservationDate, List.of(2L, 3L, 4L, 5L));

        verify(seatAvailabilityMapper, never())
                .findActiveReservationsBySlotIds(
                        1L,
                        reservationDate,
                        List.of(2L, 3L, 4L, 5L)
                );
    }

    @Test
    void rebuildsRedisProjectionAndUsesRedisWhenProjectionMissing() {
        LocalDate reservationDate =
                LocalDate.now().plusDays(1);

        StudyRoom room = enabledRoom(1L);

        ReservationSlotRange slotRange =
                new ReservationSlotRange(
                        List.of(2L, 3L, 4L, 5L),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        "08:00-10:00"
                );

        Seat seat1 = seat(
                1L,
                BizConstants.SEAT_STATUS_FREE
        );
        Seat seat2 = seat(
                2L,
                BizConstants.SEAT_STATUS_FREE
        );

        when(studyRoomMapper.findById(1L))
                .thenReturn(room);

        when(reservationSlotService.resolveSelectableRange(
                room,
                2L,
                5L
        )).thenReturn(slotRange);

        when(seatMapper.findByRoomId(1L))
                .thenReturn(List.of(seat1, seat2));

        when(bitmapService.findOccupiedSeatIds(
                1L,
                reservationDate,
                List.of(2L, 3L, 4L, 5L),
                List.of(1L, 2L)
        )).thenReturn(
                Optional.empty(),
                Optional.of(Set.of(1L))
        );

        when(bitmapRebuildService.rebuild(
                1L,
                reservationDate,
                List.of(2L, 3L, 4L, 5L)
        )).thenReturn(true);

        List<SeatAvailabilityResponse> result =
                seatAvailabilityService.findAvailableSeats(
                        1L,
                        reservationDate,
                        2L,
                        5L
                );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).status())
                .isEqualTo(BizConstants.SEAT_STATUS_RESERVED);
        assertThat(result.get(1).status())
                .isEqualTo(BizConstants.SEAT_STATUS_FREE);

        verify(bitmapRebuildService)
                .rebuild(
                        1L,
                        reservationDate,
                        List.of(2L, 3L, 4L, 5L)
                );

        verify(seatAvailabilityMapper, never())
                .findActiveReservationsBySlotIds(
                        1L,
                        reservationDate,
                        List.of(2L, 3L, 4L, 5L)
                );
    }

    @Test
    void fallsBackToMysqlWhenRedisProjectionRebuildFails() {
        LocalDate reservationDate =
                LocalDate.now().plusDays(1);

        StudyRoom room = enabledRoom(1L);

        ReservationSlotRange slotRange =
                new ReservationSlotRange(
                        List.of(2L, 3L, 4L, 5L),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        "08:00-10:00"
                );

        Seat seat1 = seat(
                1L,
                BizConstants.SEAT_STATUS_FREE
        );
        Seat seat2 = seat(
                2L,
                BizConstants.SEAT_STATUS_FREE
        );
        Seat seat3 = seat(
                3L,
                BizConstants.SEAT_STATUS_REPAIR
        );

        Reservation pendingReservation = reservation(
                1L,
                BizConstants.RESERVATION_PENDING
        );
        Reservation usingReservation = reservation(
                2L,
                BizConstants.RESERVATION_USING
        );

        when(studyRoomMapper.findById(1L))
                .thenReturn(room);

        when(reservationSlotService.resolveSelectableRange(
                room,
                2L,
                5L
        )).thenReturn(slotRange);

        when(seatMapper.findByRoomId(1L))
                .thenReturn(List.of(seat1, seat2, seat3));

        when(bitmapService.findOccupiedSeatIds(
                1L,
                reservationDate,
                List.of(2L, 3L, 4L, 5L),
                List.of(1L, 2L, 3L)
        )).thenReturn(Optional.empty());

        when(bitmapRebuildService.rebuild(
                1L,
                reservationDate,
                List.of(2L, 3L, 4L, 5L)
        )).thenReturn(false);

        when(seatAvailabilityMapper.findActiveReservationsBySlotIds(
                1L,
                reservationDate,
                List.of(2L, 3L, 4L, 5L)
        )).thenReturn(List.of(
                pendingReservation,
                usingReservation
        ));

        List<SeatAvailabilityResponse> result =
                seatAvailabilityService.findAvailableSeats(
                        1L,
                        reservationDate,
                        2L,
                        5L
                );

        assertThat(result).hasSize(3);

        assertThat(result.get(0).status())
                .isEqualTo(BizConstants.SEAT_STATUS_RESERVED);

        assertThat(result.get(1).status())
                .isEqualTo(BizConstants.SEAT_STATUS_USING);

        assertThat(result.get(2).status())
                .isEqualTo(BizConstants.SEAT_STATUS_REPAIR);

        verify(seatAvailabilityMapper)
                .findActiveReservationsBySlotIds(
                        1L,
                        reservationDate,
                        List.of(2L, 3L, 4L, 5L)
                );
    }

    @Test
    void queriesMysqlDirectlyWhenRedisProjectionDisabled() {
        LocalDate reservationDate =
                LocalDate.now().plusDays(1);

        StudyRoom room = enabledRoom(1L);

        ReservationSlotRange slotRange =
                new ReservationSlotRange(
                        List.of(2L, 3L),
                        LocalTime.of(8, 0),
                        LocalTime.of(9, 0),
                        "08:00-09:00"
                );

        seatAvailabilityService = new SeatAvailabilityService(
                seatMapper,
                studyRoomMapper,
                seatAvailabilityMapper,
                reservationSlotService,
                bitmapService,
                bitmapRebuildService,
                false
        );

        Seat seat = seat(
                1L,
                BizConstants.SEAT_STATUS_RESERVED
        );

        when(studyRoomMapper.findById(1L))
                .thenReturn(room);

        when(reservationSlotService.resolveSelectableRange(
                room,
                2L,
                3L
        )).thenReturn(slotRange);

        when(seatMapper.findByRoomId(1L))
                .thenReturn(List.of(seat));

        when(seatAvailabilityMapper.findActiveReservationsBySlotIds(
                1L,
                reservationDate,
                List.of(2L, 3L)
        )).thenReturn(List.of());

        List<SeatAvailabilityResponse> result =
                seatAvailabilityService.findAvailableSeats(
                        1L,
                        reservationDate,
                        2L,
                        3L
                );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status())
                .isEqualTo(BizConstants.SEAT_STATUS_FREE);

        verifyNoInteractions(bitmapService);
        verifyNoInteractions(bitmapRebuildService);
    }

    @Test
    void rejectsPastReservationDate() {
        LocalDate pastDate =
                LocalDate.now().minusDays(1);

        assertThatThrownBy(
                () -> seatAvailabilityService.findAvailableSeats(
                        1L,
                        pastDate,
                        2L,
                        5L
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能查询过去日期的可预约座位");

        verifyNoInteractions(
                seatMapper,
                studyRoomMapper,
                seatAvailabilityMapper,
                reservationSlotService,
                bitmapService,
                bitmapRebuildService
        );
    }

    private static StudyRoom enabledRoom(Long roomId) {
        StudyRoom room = new StudyRoom();
        room.setId(roomId);
        room.setStatus(1);
        room.setOpenTime(LocalTime.of(8, 0));
        room.setCloseTime(LocalTime.of(22, 30));
        return room;
    }

    private static Seat seat(
            Long seatId,
            Integer status) {

        Seat seat = new Seat();
        seat.setId(seatId);
        seat.setRoomId(1L);
        seat.setSeatNo("A" + seatId);
        seat.setX(seatId.intValue());
        seat.setY(1);
        seat.setHasPower(1);
        seat.setNearWindow(0);
        seat.setStatus(status);
        return seat;
    }

    private static Reservation reservation(
            Long seatId,
            Integer status) {

        Reservation reservation = new Reservation();
        reservation.setSeatId(seatId);
        reservation.setStatus(status);
        return reservation;
    }
}