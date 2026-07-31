package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.PageResult;
import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.common.UserRole;
import com.smartstudy.studyroom.dto.CheckinSignRequest;
import com.smartstudy.studyroom.dto.CreateReservationRequest;
import com.smartstudy.studyroom.dto.CreateReservationResponse;
import com.smartstudy.studyroom.dto.LoginResponse;
import com.smartstudy.studyroom.dto.MyReservationResponse;
import com.smartstudy.studyroom.dto.ReservationSlotRange;
import com.smartstudy.studyroom.dto.StatusResponse;
import com.smartstudy.studyroom.dto.UserInfoResponse;
import com.smartstudy.studyroom.dto.ViolationResponse;
import com.smartstudy.studyroom.dto.WxLoginRequest;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.entity.Violation;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import com.smartstudy.studyroom.mapper.UserMapper;
import com.smartstudy.studyroom.mapper.ViolationMapper;
import com.smartstudy.studyroom.service.AdminReservationService;
import com.smartstudy.studyroom.service.AuthService;
import com.smartstudy.studyroom.service.CheckinService;
import com.smartstudy.studyroom.service.ConfigService;
import com.smartstudy.studyroom.service.ReservationService;
import com.smartstudy.studyroom.service.ReservationLifecycleService;
import com.smartstudy.studyroom.service.ReservationSlotService;
import com.smartstudy.studyroom.service.ReservationTimeoutService;
import com.smartstudy.studyroom.service.RoomStatsService;
import com.smartstudy.studyroom.service.TokenService;
import com.smartstudy.studyroom.service.UserService;
import com.smartstudy.studyroom.service.ViolationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreBusinessServiceTest {

    @Test
    void wxLoginCreatesUserAndReturnsToken() {
        UserMapper userMapper = mock(UserMapper.class);
        TokenService tokenService = new TokenService(
                "test-secret",
                3600,
                java.time.Clock.systemUTC()
        );
        AuthService authService = new AuthService(
                userMapper,
                tokenService
        );

        when(userMapper.findByOpenid("test-openid"))
                .thenReturn(null);
        when(userMapper.findByStudentNo("20240001"))
                .thenReturn(null);

        when(userMapper.insert(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(10L);
                    user.setStatus(
                            BizConstants.USER_STATUS_NORMAL
                    );
                    return 1;
                });

        WxLoginRequest request = new WxLoginRequest();
        request.setCode("openid:test-openid");
        request.setStudentNo("20240001");
        request.setRealName("张三");
        request.setNickname("微信昵称");

        LoginResponse response = authService.login(request);

        assertThat(response.getToken())
                .startsWith("st.");
        assertThat(tokenService.requireUserId("Bearer " + response.getToken()))
                .isEqualTo(10L);
        assertThat(response.getRole())
                .isEqualTo(UserRole.USER.name());
        assertThat(response.getStudentNo())
                .isEqualTo("20240001");
    }

    @Test
    void userInfoReturnsCurrentUserProfile() {
        UserMapper userMapper = mock(UserMapper.class);
        UserService userService = new UserService(userMapper);

        User user = normalUser(1L);

        when(userMapper.findById(1L)).thenReturn(user);

        UserInfoResponse response =
                userService.getUserInfo(1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getCreditScore()).isEqualTo(100);
    }

    @Test
    void createReservationUsesServerResolvedSlotRange() {
        ReservationFixture fixture = new ReservationFixture();

        when(fixture.userMapper.findById(1L))
                .thenReturn(normalUser(1L));

        when(fixture.seatMapper.findById(1L))
                .thenReturn(seat(
                        1L,
                        1L,
                        "A001",
                        BizConstants.SEAT_STATUS_FREE
                ));

        when(fixture.studyRoomMapper.findById(1L))
                .thenReturn(room(1L));

        when(fixture.reservationSlotService.resolveSelectableRange(
                any(StudyRoom.class),
                eq(2L),
                eq(5L)
        )).thenReturn(new ReservationSlotRange(
                List.of(2L, 3L, 4L, 5L),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                "08:00-10:00"
        ));

        when(fixture.reservationMapper.countSeatConflict(
                anyLong(),
                any(),
                any(),
                any()
        )).thenReturn(0);

        when(fixture.reservationMapper.countUserSlotConflict(
                anyLong(),
                any(),
                any(),
                any()
        )).thenReturn(0);

        when(fixture.reservationMapper.countUserDailyActive(
                anyLong(),
                any()
        )).thenReturn(0);

        when(fixture.configService.getIntConfig(
                eq(BizConstants.CONFIG_RESERVATION_MAX_HOURS),
                anyInt()
        )).thenReturn(4);

        when(fixture.configService.getIntConfig(
                eq(BizConstants.CONFIG_MAX_RESERVATION_PER_DAY),
                anyInt()
        )).thenReturn(2);

        when(fixture.reservationMapper.insert(
                any(Reservation.class)
        )).thenAnswer(invocation -> {
            Reservation reservation =
                    invocation.getArgument(0);
            reservation.setId(1001L);
            return 1;
        });

        CreateReservationRequest request = reservationRequest(
                1L,
                LocalDate.now().plusDays(1),
                2L,
                5L
        );

        CreateReservationResponse response =
                fixture.reservationService.createReservation(
                        1L,
                        request
                );

        assertThat(response.getReservationId())
                .isEqualTo(1001L);
        assertThat(response.getStatus())
                .isEqualTo(BizConstants.RESERVATION_PENDING);

        ArgumentCaptor<Reservation> captor =
                ArgumentCaptor.forClass(Reservation.class);

        verify(fixture.reservationMapper)
                .insert(captor.capture());

        Reservation inserted = captor.getValue();

        assertThat(inserted.getTimeSlot())
                .isEqualTo("08:00-10:00");
        assertThat(inserted.getStartTime())
                .isEqualTo(LocalTime.of(8, 0));
        assertThat(inserted.getEndTime())
                .isEqualTo(LocalTime.of(10, 0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservationSlotOccupancy>> occupancyCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(fixture.reservationSlotOccupancyMapper)
                .batchInsert(occupancyCaptor.capture());

        List<ReservationSlotOccupancy> occupancies =
                occupancyCaptor.getValue();

        assertThat(occupancies)
                .extracting(ReservationSlotOccupancy::getSlotId)
                .containsExactly(2L, 3L, 4L, 5L);
        assertThat(occupancies)
                .allSatisfy(occupancy -> {
                    assertThat(occupancy.getReservationId())
                            .isEqualTo(1001L);
                    assertThat(occupancy.getUserId())
                            .isEqualTo(1L);
                    assertThat(occupancy.getSeatId())
                            .isEqualTo(1L);
                    assertThat(occupancy.getRoomId())
                            .isEqualTo(1L);
                    assertThat(occupancy.getReservationDate())
                            .isEqualTo(request.getReservationDate());
                });

    }

    @Test
    void cancelReservationMarksCanceledAndReleasesSlotOccupancies() {
        ReservationFixture fixture = new ReservationFixture();

        Reservation reservation = pendingReservation(
                1001L,
                1L,
                1L,
                1L
        );

        when(fixture.reservationMapper.findById(1001L))
                .thenReturn(reservation);

        when(fixture.reservationMapper.updateStatusIfCurrent(
                1001L,
                BizConstants.RESERVATION_PENDING,
                BizConstants.RESERVATION_CANCELED
        )).thenReturn(1);

        when(fixture.reservationMapper.countActiveBySeat(1L))
                .thenReturn(0);

        fixture.reservationService.cancelReservation(
                1L,
                1001L
        );

        verify(fixture.reservationSlotOccupancyMapper)
                .deleteByReservationId(1001L);

    }

    @Test
    void adminCancelReservationReleasesSlotOccupancies() {
        ReservationFixture fixture = new ReservationFixture();
        AdminReservationService adminReservationService =
                new AdminReservationService(
                        fixture.reservationMapper,
                        fixture.reservationLifecycleService
                );

        Reservation reservation = pendingReservation(
                1001L,
                1L,
                1L,
                1L
        );

        when(fixture.reservationMapper.findById(1001L))
                .thenReturn(reservation);
        when(fixture.reservationMapper.updateStatusIfCurrentIn(
                1001L,
                ReservationStatus.adminCancellableCodes(),
                BizConstants.RESERVATION_CANCELED
        )).thenReturn(1);
        when(fixture.reservationMapper.countActiveBySeatExclude(
                1L,
                1001L
        )).thenReturn(0);

        adminReservationService.cancel(
                1001L,
                "maintenance"
        );

        verify(fixture.reservationSlotOccupancyMapper)
                .deleteByReservationId(1001L);
        verify(fixture.roomStatsService)
                .refreshRoomSeatStats(1L);
    }

    @Test
    void myReservationsReturnsPageResult() {
        ReservationFixture fixture = new ReservationFixture();

        MyReservationResponse record =
                new MyReservationResponse();
        record.setReservationId(1001L);

        when(fixture.reservationMapper.countMy(1L, null))
                .thenReturn(1L);

        when(fixture.reservationMapper.findMy(
                1L,
                null,
                0,
                10
        )).thenReturn(Collections.singletonList(record));

        PageResult<MyReservationResponse> page =
                fixture.reservationService.findMyReservations(
                        1L,
                        null,
                        1,
                        10
                );

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
    }

    @Test
    void checkinSignMarksReservationUsing() {
        ReservationFixture fixture = new ReservationFixture();

        CheckinService checkinService = new CheckinService(
                fixture.reservationService,
                fixture.seatMapper,
                fixture.configService,
                fixture.reservationLifecycleService
        );

        Reservation reservation = pendingReservation(
                1001L,
                1L,
                1L,
                1L
        );

        reservation.setReservationDate(LocalDate.now());
        reservation.setStartTime(
                LocalTime.now().minusMinutes(1).withNano(0)
        );

        when(fixture.reservationMapper.findById(1001L))
                .thenReturn(reservation);

        when(fixture.seatMapper.findById(1L))
                .thenReturn(seat(
                        1L,
                        1L,
                        "A001",
                        BizConstants.SEAT_STATUS_RESERVED
                ));

        when(fixture.configService.getIntConfig(
                eq(BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES),
                anyInt()
        )).thenReturn(15);

        when(fixture.reservationMapper.markSigned(
                eq(1001L),
                eq(BizConstants.RESERVATION_PENDING),
                eq(BizConstants.RESERVATION_USING),
                any()
        )).thenReturn(1);

        CheckinSignRequest request = new CheckinSignRequest();
        request.setReservationId(1001L);
        request.setSeatCode("1-A001");

        StatusResponse response = checkinService.sign(
                1L,
                request.getReservationId(),
                request.getSeatCode()
        );

        assertThat(response.getStatus())
                .isEqualTo(BizConstants.RESERVATION_USING);

    }

    @Test
    void leaveMarksFinishedAndReleasesSlotOccupancies() {
        ReservationFixture fixture = new ReservationFixture();

        CheckinService checkinService = new CheckinService(
                fixture.reservationService,
                fixture.seatMapper,
                fixture.configService,
                fixture.reservationLifecycleService
        );

        Reservation reservation = pendingReservation(
                1001L,
                1L,
                1L,
                1L
        );
        reservation.setStatus(
                BizConstants.RESERVATION_USING
        );

        when(fixture.reservationMapper.findById(1001L))
                .thenReturn(reservation);

        when(fixture.reservationMapper.markLeft(
                eq(1001L),
                eq(BizConstants.RESERVATION_USING),
                eq(BizConstants.RESERVATION_FINISHED),
                any()
        )).thenReturn(1);

        when(fixture.reservationMapper.countActiveBySeatExclude(
                1L,
                1001L
        )).thenReturn(0);

        checkinService.leave(1L, 1001L);

        verify(fixture.reservationSlotOccupancyMapper)
                .deleteByReservationId(1001L);

    }

    @Test
    void violationServiceReturnsMyViolations() {
        ViolationMapper violationMapper =
                mock(ViolationMapper.class);

        ViolationService violationService =
                new ViolationService(violationMapper);

        ViolationResponse response =
                new ViolationResponse();
        response.setViolationId(1L);

        when(violationMapper.findByUserId(1L))
                .thenReturn(Collections.singletonList(response));

        List<ViolationResponse> result =
                violationService.findMyViolations(1L);

        assertThat(result).hasSize(1);
    }

        @Test
        void timeoutTaskMarksViolationBansAndReleasesSlotOccupancies() {
        ReservationMapper reservationMapper =
                mock(ReservationMapper.class);
        ReservationSlotOccupancyMapper reservationSlotOccupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);
        ViolationMapper violationMapper =
                mock(ViolationMapper.class);
        UserMapper userMapper =
                mock(UserMapper.class);
        ConfigService configService =
                mock(ConfigService.class);
        RoomStatsService roomStatsService =
                mock(RoomStatsService.class);
        ReservationLifecycleService reservationLifecycleService =
                new ReservationLifecycleService(
                        reservationMapper,
                        reservationSlotOccupancyMapper,
                        roomStatsService
                );

        ReservationTimeoutService service =
                new ReservationTimeoutService(
                        reservationMapper,
                        reservationLifecycleService,
                        violationMapper,
                        userMapper,
                        configService
                );

        Reservation reservation = pendingReservation(
                1001L,
                1L,
                2L,
                1L
        );

        LocalDateTime overdueStart =
                LocalDateTime.now().minusMinutes(30).withNano(0);

        reservation.setReservationDate(
                overdueStart.toLocalDate()
        );
        reservation.setStartTime(
                overdueStart.toLocalTime()
        );

        when(configService.getIntConfig(
                eq(BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES),
                anyInt()
        )).thenReturn(15);

        when(configService.getIntConfig(
                eq(BizConstants.CONFIG_VIOLATION_LIMIT),
                anyInt()
        )).thenReturn(3);

        when(reservationMapper.findByStatus(
                BizConstants.RESERVATION_PENDING
        ))
                .thenReturn(Collections.singletonList(reservation));

        when(reservationMapper.updateStatusIfCurrent(
                1001L,
                BizConstants.RESERVATION_PENDING,
                BizConstants.RESERVATION_VIOLATED
        )).thenReturn(1);

        User user = normalUser(1L);
        user.setViolationCount(3);

        when(userMapper.findById(1L)).thenReturn(user);

        int handled = service.releaseTimeoutReservations();

        assertThat(handled).isEqualTo(1);

        verify(violationMapper).insert(any(Violation.class));
        verify(userMapper).banUser(1L);
        verify(reservationSlotOccupancyMapper)
                .deleteByReservationId(1001L);

    }

    private static CreateReservationRequest reservationRequest(
            Long seatId,
            LocalDate date,
            Long startSlotId,
            Long endSlotId) {

        CreateReservationRequest request =
                new CreateReservationRequest();

        request.setSeatId(seatId);
        request.setRoomId(1L);
        request.setReservationDate(date);
        request.setStartSlotId(startSlotId);
        request.setEndSlotId(endSlotId);

        return request;
    }

    private static User normalUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setStudentNo("20240001");
        user.setRealName("张三");
        user.setCreditScore(100);
        user.setViolationCount(0);
        user.setStatus(BizConstants.USER_STATUS_NORMAL);
        return user;
    }

    private static Seat seat(
            Long seatId,
            Long roomId,
            String seatNo,
            int status) {

        Seat seat = new Seat();
        seat.setId(seatId);
        seat.setRoomId(roomId);
        seat.setSeatNo(seatNo);
        seat.setStatus(status);
        return seat;
    }

    private static StudyRoom room(Long roomId) {
        StudyRoom room = new StudyRoom();
        room.setId(roomId);
        room.setStatus(1);
        room.setTotalSeats(10);
        room.setOpenTime(LocalTime.of(8, 0));
        room.setCloseTime(LocalTime.of(22, 30));
        return room;
    }

    private static Reservation pendingReservation(
            Long reservationId,
            Long userId,
            Long seatId,
            Long roomId) {

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setUserId(userId);
        reservation.setSeatId(seatId);
        reservation.setRoomId(roomId);
        reservation.setReservationDate(
                LocalDate.now().plusDays(1)
        );
        reservation.setTimeSlot("08:00-10:00");
        reservation.setStartTime(LocalTime.of(8, 0));
        reservation.setEndTime(LocalTime.of(10, 0));
        reservation.setStatus(
                BizConstants.RESERVATION_PENDING
        );
        return reservation;
    }

    private static class ReservationFixture {

        private final ReservationMapper reservationMapper =
                mock(ReservationMapper.class);

        private final ReservationSlotOccupancyMapper
                reservationSlotOccupancyMapper =
                mock(ReservationSlotOccupancyMapper.class);

        private final SeatMapper seatMapper =
                mock(SeatMapper.class);

        private final StudyRoomMapper studyRoomMapper =
                mock(StudyRoomMapper.class);

        private final UserMapper userMapper =
                mock(UserMapper.class);

        private final ConfigService configService =
                mock(ConfigService.class);

        private final RoomStatsService roomStatsService =
                mock(RoomStatsService.class);

        private final ReservationSlotService reservationSlotService =
                mock(ReservationSlotService.class);

        private final UserService userService =
                new UserService(userMapper);

        private final ReservationLifecycleService
                reservationLifecycleService =
                new ReservationLifecycleService(
                        reservationMapper,
                        reservationSlotOccupancyMapper,
                        roomStatsService
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
                        reservationLifecycleService
                );
    }
}
