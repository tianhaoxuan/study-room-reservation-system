package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.dto.CreateReservationRequest;
import com.smartstudy.studyroom.dto.CreateReservationResponse;
import com.smartstudy.studyroom.dto.ReservationSlotRange;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import com.smartstudy.studyroom.redis.RedisSeatPreOccupyService;
import com.smartstudy.studyroom.redis.ReservationSeatBitmapProjectionService;
import com.smartstudy.studyroom.redis.SeatPreOccupyResult;
import com.smartstudy.studyroom.redis.SeatPreOccupyStatus;
import com.smartstudy.studyroom.service.ConfigService;
import com.smartstudy.studyroom.service.ReservationLifecycleService;
import com.smartstudy.studyroom.service.ReservationService;
import com.smartstudy.studyroom.service.ReservationSlotService;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import com.smartstudy.studyroom.service.RoomStatsService;
import com.smartstudy.studyroom.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationServiceRedisPreOccupyTest {

    @Test
    void shouldReturnExistingReservationWhenRequestIdAlreadyCreated() {
        Fixture fixture = new Fixture();

        Reservation existing = new Reservation();
        existing.setId(1001L);
        existing.setUserId(1L);
        existing.setRequestId("req-1");
        existing.setStatus(BizConstants.RESERVATION_PENDING);

        when(fixture.userMapper.findById(1L))
                .thenReturn(normalUser(1L));
        when(fixture.reservationMapper.findByUserIdAndRequestId(
                1L,
                "req-1"
        )).thenReturn(existing);

        CreateReservationResponse response =
                fixture.reservationService.createReservation(
                        1L,
                        request("req-1")
                );

        assertThat(response.getReservationId())
                .isEqualTo(1001L);
        assertThat(response.getStatus())
                .isEqualTo(BizConstants.RESERVATION_PENDING);

        verify(fixture.redisSeatPreOccupyService, never())
                .preOccupy(any(), anyLong(), anyLong(), any(), any(), anyLong());
        verify(fixture.reservationMapper, never())
                .insert(any(Reservation.class));
    }

    @Test
    void shouldRejectRedisSeatPreOccupyConflict() {
        Fixture fixture = new Fixture();
        fixture.givenCreatableReservation();

        when(fixture.redisSeatPreOccupyService.preOccupy(
                eq("req-1"),
                eq(1L),
                eq(1L),
                eq(fixture.reservationDate),
                eq(List.of(2L, 3L, 4L, 5L)),
                eq(1L)
        )).thenReturn(SeatPreOccupyResult.of(
                SeatPreOccupyStatus.SEAT_CONFLICT,
                "seat conflict"
        ));

        assertThatThrownBy(() ->
                fixture.reservationService.createReservation(
                        1L,
                        request("req-1")
                )
        ).isInstanceOf(BusinessException.class);

        verify(fixture.reservationMapper, never())
                .insert(any(Reservation.class));
    }

    @Test
    void shouldReleasePreOccupyWhenDatabaseOccupancyFails() {
        Fixture fixture = new Fixture();
        fixture.givenCreatableReservation();

        when(fixture.redisSeatPreOccupyService.preOccupy(
                eq("req-1"),
                eq(1L),
                eq(1L),
                eq(fixture.reservationDate),
                eq(List.of(2L, 3L, 4L, 5L)),
                eq(1L)
        )).thenReturn(SeatPreOccupyResult.of(
                SeatPreOccupyStatus.PREOCCUPIED,
                "preoccupied"
        ));

        when(fixture.reservationMapper.insert(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation reservation = invocation.getArgument(0);
                    reservation.setId(1001L);
                    return 1;
                });

        when(fixture.reservationSlotOccupancyMapper.batchInsert(any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() ->
                fixture.reservationService.createReservation(
                        1L,
                        request("req-1")
                )
        ).isInstanceOf(BusinessException.class);

        verify(fixture.redisSeatPreOccupyService).release(
                "req-1",
                1L,
                1L,
                fixture.reservationDate,
                List.of(2L, 3L, 4L, 5L),
                1L
        );
    }

    @Test
    void shouldContinueWithMysqlWhenRedisPreOccupyFails() {
        Fixture fixture = new Fixture();
        fixture.givenCreatableReservation();

        when(fixture.redisSeatPreOccupyService.preOccupy(
                eq("req-1"),
                eq(1L),
                eq(1L),
                eq(fixture.reservationDate),
                eq(List.of(2L, 3L, 4L, 5L)),
                eq(1L)
        )).thenReturn(SeatPreOccupyResult.of(
                SeatPreOccupyStatus.FAILED,
                "redis down"
        ));

        when(fixture.reservationMapper.insert(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation reservation = invocation.getArgument(0);
                    reservation.setId(1001L);
                    return 1;
                });

        ReservationTimeoutMessage timeoutMessage =
                new ReservationTimeoutMessage();
        timeoutMessage.setId(2001L);
        timeoutMessage.setReservationId(1001L);
        timeoutMessage.setDeadlineAt(LocalDateTime.of(
                fixture.reservationDate,
                LocalTime.of(8, 15)
        ));

        when(fixture.reservationTimeoutMessageService.createPending(
                eq(1001L),
                eq(timeoutMessage.getDeadlineAt())
        )).thenReturn(timeoutMessage);

        CreateReservationResponse response =
                fixture.reservationService.createReservation(
                        1L,
                        request("req-1")
                );

        assertThat(response.getReservationId())
                .isEqualTo(1001L);

        verify(fixture.redisSeatPreOccupyService, never())
                .release(any(), anyLong(), anyLong(), any(), any(), anyLong());
    }

    private static CreateReservationRequest request(String requestId) {
        CreateReservationRequest request =
                new CreateReservationRequest();
        request.setRequestId(requestId);
        request.setSeatId(1L);
        request.setRoomId(1L);
        request.setReservationDate(LocalDate.now().plusDays(1));
        request.setStartSlotId(2L);
        request.setEndSlotId(5L);
        return request;
    }

    private static User normalUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setStatus(BizConstants.USER_STATUS_NORMAL);
        user.setViolationCount(0);
        user.setCreditScore(100);
        return user;
    }

    private static Seat seat() {
        Seat seat = new Seat();
        seat.setId(1L);
        seat.setRoomId(1L);
        seat.setStatus(BizConstants.SEAT_STATUS_FREE);
        return seat;
    }

    private static StudyRoom room() {
        StudyRoom room = new StudyRoom();
        room.setId(1L);
        room.setStatus(1);
        room.setOpenTime(LocalTime.of(8, 0));
        room.setCloseTime(LocalTime.of(22, 30));
        return room;
    }

    private static class Fixture {

        private final LocalDate reservationDate =
                LocalDate.now().plusDays(1);

        private final ReservationMapper reservationMapper =
                mock(ReservationMapper.class);

        private final ReservationSlotOccupancyMapper
                reservationSlotOccupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);

        private final SeatMapper seatMapper =
                mock(SeatMapper.class);

        private final StudyRoomMapper studyRoomMapper =
                mock(StudyRoomMapper.class);

        private final com.smartstudy.studyroom.mapper.UserMapper userMapper =
                mock(com.smartstudy.studyroom.mapper.UserMapper.class);

        private final ConfigService configService =
                mock(ConfigService.class);

        private final RoomStatsService roomStatsService =
                mock(RoomStatsService.class);

        private final ReservationSlotService reservationSlotService =
                mock(ReservationSlotService.class);

        private final ReservationTimeoutMessageService
                reservationTimeoutMessageService =
                mock(ReservationTimeoutMessageService.class);

        private final ReservationSeatBitmapProjectionService
                bitmapProjectionService =
                mock(ReservationSeatBitmapProjectionService.class);

        private final RedisSeatPreOccupyService redisSeatPreOccupyService =
                mock(RedisSeatPreOccupyService.class);

        private final ApplicationEventPublisher eventPublisher =
                mock(ApplicationEventPublisher.class);

        private final UserService userService =
                new UserService(userMapper);

        private final ReservationLifecycleService
                reservationLifecycleService =
                new ReservationLifecycleService(
                        reservationMapper,
                        reservationSlotOccupancyMapper,
                        roomStatsService,
                        bitmapProjectionService
                );

        private final ReservationService reservationService =
                new ReservationService(
                        reservationMapper,
                        reservationSlotOccupancyMapper,
                        seatMapper,
                        studyRoomMapper,
                        userService,
                        configService,
                        roomStatsService,
                        reservationSlotService,
                        reservationLifecycleService,
                        reservationTimeoutMessageService,
                        bitmapProjectionService,
                        redisSeatPreOccupyService,
                        eventPublisher
                );

        private void givenCreatableReservation() {
            when(userMapper.findById(1L))
                    .thenReturn(normalUser(1L));

            when(reservationMapper.findByUserIdAndRequestId(
                    1L,
                    "req-1"
            )).thenReturn(null);

            when(seatMapper.findById(1L))
                    .thenReturn(seat());

            when(studyRoomMapper.findById(1L))
                    .thenReturn(room());

            when(reservationSlotService.resolveSelectableRange(
                    any(StudyRoom.class),
                    eq(2L),
                    eq(5L)
            )).thenReturn(new ReservationSlotRange(
                    List.of(2L, 3L, 4L, 5L),
                    LocalTime.of(8, 0),
                    LocalTime.of(10, 0),
                    "08:00-10:00"
            ));

            when(reservationMapper.countSeatConflict(
                    anyLong(),
                    any(),
                    any(),
                    any()
            )).thenReturn(0);

            when(reservationMapper.countUserSlotConflict(
                    anyLong(),
                    any(),
                    any(),
                    any()
            )).thenReturn(0);

            when(reservationMapper.countUserDailyActive(
                    anyLong(),
                    any()
            )).thenReturn(0);

            when(configService.getIntConfig(
                    eq(BizConstants.CONFIG_RESERVATION_MAX_HOURS),
                    anyInt()
            )).thenReturn(4);

            when(configService.getIntConfig(
                    eq(BizConstants.CONFIG_MAX_RESERVATION_PER_DAY),
                    anyInt()
            )).thenReturn(2);

            when(configService.getIntConfig(
                    eq(BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES),
                    anyInt()
            )).thenReturn(15);
        }
    }
}