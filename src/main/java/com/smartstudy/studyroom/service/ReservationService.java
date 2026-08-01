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
import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import com.smartstudy.studyroom.messaging.CheckinTimeoutScheduledEvent;
import com.smartstudy.studyroom.redis.ReservationSeatBitmapProjectionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ReservationTimeoutMessageService
            reservationTimeoutMessageService;
    private final ReservationSeatBitmapProjectionService
            bitmapProjectionService;
    private final ApplicationEventPublisher eventPublisher;

    public ReservationService(
            ReservationMapper reservationMapper,
            ReservationSlotOccupancyMapper reservationSlotOccupancyMapper,
            SeatMapper seatMapper,
            StudyRoomMapper studyRoomMapper,
            UserService userService,
            ConfigService configService,
            RoomStatsService roomStatsService,
            ReservationSlotService reservationSlotService,
            ReservationLifecycleService reservationLifecycleService,
            ReservationTimeoutMessageService reservationTimeoutMessageService,
            ReservationSeatBitmapProjectionService bitmapProjectionService,
            ApplicationEventPublisher eventPublisher) {

        this.reservationMapper = reservationMapper;
        this.reservationSlotOccupancyMapper =
                reservationSlotOccupancyMapper;
        this.seatMapper = seatMapper;
        this.studyRoomMapper = studyRoomMapper;
        this.userService = userService;
        this.configService = configService;
        this.roomStatsService = roomStatsService;
        this.reservationSlotService = reservationSlotService;
        this.reservationLifecycleService = reservationLifecycleService;
        this.reservationTimeoutMessageService =
                reservationTimeoutMessageService;
        this.bitmapProjectionService = bitmapProjectionService;
        this.eventPublisher = eventPublisher;
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
                    "账号已封禁，暂时不能预约"
            );
        }

        LocalDate today = LocalDate.now();

        if (request.getReservationDate().isBefore(today)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "不能预约过去日期"
            );
        }

        Seat seat = seatMapper.findById(request.getSeatId());

        if (seat == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "座位不存在"
            );
        }

        if (!request.getRoomId().equals(seat.getRoomId())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "座位不属于当前自习室"
            );
        }

        if (Integer.valueOf(BizConstants.SEAT_STATUS_REPAIR)
                .equals(seat.getStatus())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "维修中的座位不能预约"
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
                    "自习室不存在或未开放"
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
                    "不能预约已经开始的时段"
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
                    "预约时长超过系统限制"
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
                    "该座位在所选时间范围内已被预约"
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
                    "所选时间范围与已有预约冲突"
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
                    "当天预约次数已达上限"
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

        List<ReservationSlotOccupancy> occupancies =
                createSlotOccupancies(
                        reservation,
                        userId,
                        slotRange.slotIds()
                );

        bitmapProjectionService.projectOccupiedAfterCommit(
                occupancies
        );

        int checkinLimitMinutes = configService.getIntConfig(
                BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES,
                15
        );

        LocalDateTime deadlineAt = LocalDateTime.of(
                request.getReservationDate(),
                slotRange.startTime()
        ).plusMinutes(checkinLimitMinutes);

        ReservationTimeoutMessage timeoutMessage =
                reservationTimeoutMessageService.createPending(
                        reservation.getId(),
                        deadlineAt
                );

        eventPublisher.publishEvent(new CheckinTimeoutScheduledEvent(
                timeoutMessage.getId(),
                reservation.getId(),
                deadlineAt
        ));

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
                    "预约不存在"
            );
        }

        if (!userId.equals(reservation.getUserId())) {
            throw new BusinessException(
                    StatusCode.FORBIDDEN,
                    "只能操作自己的预约"
            );
        }

        return reservation;
    }

    private List<ReservationSlotOccupancy> createSlotOccupancies(
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
            return occupancies;
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "所选座位或时间段已被占用，请刷新后重试"
            );
        }
    }
}