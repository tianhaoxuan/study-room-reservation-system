package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.PageResult;
import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.CreateReservationRequest;
import com.smartstudy.studyroom.dto.CreateReservationResponse;
import com.smartstudy.studyroom.dto.MyReservationResponse;
import com.smartstudy.studyroom.dto.ReservationSlotRange;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final ReservationSlotOccupancyMapper reservationSlotOccupancyMapper;
    private final SeatMapper seatMapper;
    private final StudyRoomMapper studyRoomMapper;
    private final UserService userService;
    private final ConfigService configService;
    private final RoomStatsService roomStatsService;
    private final ReservationSlotService reservationSlotService;
    private final ReservationLifecycleService reservationLifecycleService;

    public ReservationService(
            ReservationMapper reservationMapper,
            ReservationSlotOccupancyMapper reservationSlotOccupancyMapper,
            SeatMapper seatMapper,
            StudyRoomMapper studyRoomMapper,
            UserService userService,
            ConfigService configService,
            RoomStatsService roomStatsService,
            ReservationSlotService reservationSlotService,
            ReservationLifecycleService reservationLifecycleService) {

        this.reservationMapper = reservationMapper;
        this.reservationSlotOccupancyMapper = reservationSlotOccupancyMapper;
        this.seatMapper = seatMapper;
        this.studyRoomMapper = studyRoomMapper;
        this.userService = userService;
        this.configService = configService;
        this.roomStatsService = roomStatsService;
        this.reservationSlotService = reservationSlotService;
        this.reservationLifecycleService = reservationLifecycleService;
    }

    @Transactional
    public CreateReservationResponse createReservation(
            Long userId,
            CreateReservationRequest request) {

        User user = userService.requireUser(userId);

        if (!Integer.valueOf(BizConstants.USER_STATUS_NORMAL)
                .equals(user.getStatus())) {
            throw new BusinessException(
                    StatusCode.FORBIDDEN,
                    "\u8d26\u53f7\u5df2\u5c01\u7981\uff0c\u6682\u65f6\u4e0d\u80fd\u9884\u7ea6"
            );
        }

        LocalDate today = LocalDate.now();

        if (request.getReservationDate().isBefore(today)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u4e0d\u80fd\u9884\u7ea6\u8fc7\u53bb\u65e5\u671f"
            );
        }

        Seat seat = seatMapper.findById(request.getSeatId());

        if (seat == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u5ea7\u4f4d\u4e0d\u5b58\u5728"
            );
        }

        if (!request.getRoomId().equals(seat.getRoomId())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u5ea7\u4f4d\u4e0d\u5c5e\u4e8e\u5f53\u524d\u81ea\u4e60\u5ba4"
            );
        }

        if (Integer.valueOf(BizConstants.SEAT_STATUS_REPAIR)
                .equals(seat.getStatus())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u7ef4\u4fee\u4e2d\u7684\u5ea7\u4f4d\u4e0d\u80fd\u9884\u7ea6"
            );
        }

        StudyRoom room = studyRoomMapper.findById(
                request.getRoomId()
        );

        if (room == null
                || room.getStatus() == null
                || room.getStatus() != 1) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u81ea\u4e60\u5ba4\u4e0d\u5b58\u5728\u6216\u672a\u5f00\u653e"
            );
        }

        ReservationSlotRange slotRange =
                reservationSlotService.resolveSelectableRange(
                        room,
                        request.getStartSlotId(),
                        request.getEndSlotId()
                );

        if (request.getReservationDate().isEqual(today)
                && !slotRange.startTime().isAfter(LocalTime.now())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u4e0d\u80fd\u9884\u7ea6\u5df2\u7ecf\u5f00\u59cb\u7684\u65f6\u6bb5"
            );
        }

        long durationMinutes = Duration.between(
                slotRange.startTime(),
                slotRange.endTime()
        ).toMinutes();

        int maxHours = configService.getIntConfig(
                BizConstants.CONFIG_RESERVATION_MAX_HOURS,
                4
        );

        if (durationMinutes > maxHours * 60L) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u9884\u7ea6\u65f6\u957f\u8d85\u8fc7\u7cfb\u7edf\u9650\u5236"
            );
        }

        if (reservationMapper.countSeatConflict(
                request.getSeatId(),
                request.getReservationDate(),
                slotRange.startTime(),
                slotRange.endTime()
        ) > 0) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u8be5\u5ea7\u4f4d\u5728\u6240\u9009\u65f6\u95f4\u8303\u56f4\u5185\u5df2\u88ab\u9884\u7ea6"
            );
        }

        if (reservationMapper.countUserSlotConflict(
                userId,
                request.getReservationDate(),
                slotRange.startTime(),
                slotRange.endTime()
        ) > 0) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u6240\u9009\u65f6\u95f4\u8303\u56f4\u4e0e\u5df2\u6709\u9884\u7ea6\u51b2\u7a81"
            );
        }

        int maxDaily = configService.getIntConfig(
                BizConstants.CONFIG_MAX_RESERVATION_PER_DAY,
                2
        );

        if (reservationMapper.countUserDailyActive(
                userId,
                request.getReservationDate()
        ) >= maxDaily) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u5f53\u5929\u9884\u7ea6\u6b21\u6570\u5df2\u8fbe\u4e0a\u9650"
            );
        }

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSeatId(request.getSeatId());
        reservation.setRoomId(request.getRoomId());
        reservation.setReservationDate(
                request.getReservationDate()
        );
        reservation.setTimeSlot(slotRange.timeSlot());
        reservation.setStartTime(slotRange.startTime());
        reservation.setEndTime(slotRange.endTime());
        reservation.setStatus(
                ReservationStatus.PENDING_CHECKIN.code()
        );

        reservationMapper.insert(reservation);
        createSlotOccupancies(
                reservation,
                userId,
                slotRange.slotIds()
        );

        roomStatsService.refreshRoomSeatStats(
                request.getRoomId()
        );

        return new CreateReservationResponse(
                reservation.getId(),
                reservation.getStatus()
        );
    }

    @Transactional
    public void cancelReservation(
            Long userId,
            Long reservationId) {

        Reservation reservation = requireOwnReservation(
                userId,
                reservationId
        );

        reservationLifecycleService.cancelByUser(reservation);
    }

    public PageResult<MyReservationResponse> findMyReservations(
            Long userId,
            Integer status,
            Integer pageNum,
            Integer pageSize) {

        int safePageNum =
                pageNum == null || pageNum < 1
                        ? 1
                        : pageNum;

        int safePageSize =
                pageSize == null || pageSize < 1
                        ? 10
                        : Math.min(pageSize, 100);

        int offset = (safePageNum - 1) * safePageSize;

        long total = reservationMapper.countMy(
                userId,
                status
        );

        List<MyReservationResponse> records =
                reservationMapper.findMy(
                        userId,
                        status,
                        offset,
                        safePageSize
                );

        return new PageResult<>(
                total,
                records
        );
    }

    public Reservation requireOwnReservation(
            Long userId,
            Long reservationId) {

        Reservation reservation =
                reservationMapper.findById(reservationId);

        if (reservation == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u9884\u7ea6\u4e0d\u5b58\u5728"
            );
        }

        if (!userId.equals(reservation.getUserId())) {
            throw new BusinessException(
                    StatusCode.FORBIDDEN,
                    "\u53ea\u80fd\u64cd\u4f5c\u81ea\u5df1\u7684\u9884\u7ea6"
            );
        }

        return reservation;
    }

    private void createSlotOccupancies(
            Reservation reservation,
            Long userId,
            List<Long> slotIds) {

        List<ReservationSlotOccupancy> occupancies =
                slotIds.stream()
                        .map(slotId -> {
                            ReservationSlotOccupancy occupancy =
                                    new ReservationSlotOccupancy();
                            occupancy.setReservationId(
                                    reservation.getId()
                            );
                            occupancy.setUserId(userId);
                            occupancy.setSeatId(
                                    reservation.getSeatId()
                            );
                            occupancy.setRoomId(
                                    reservation.getRoomId()
                            );
                            occupancy.setReservationDate(
                                    reservation.getReservationDate()
                            );
                            occupancy.setSlotId(slotId);
                            return occupancy;
                        })
                        .toList();

        try {
            reservationSlotOccupancyMapper.batchInsert(occupancies);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u6240\u9009\u5ea7\u4f4d\u6216\u65f6\u95f4\u6bb5\u5df2\u88ab\u5360\u7528\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5"
            );
        }
    }
}
