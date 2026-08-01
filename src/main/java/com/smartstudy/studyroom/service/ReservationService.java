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
import com.smartstudy.studyroom.redis.RedisSeatPreOccupyService;
import com.smartstudy.studyroom.redis.ReservationSeatBitmapProjectionService;
import com.smartstudy.studyroom.redis.SeatPreOccupyResult;
import com.smartstudy.studyroom.redis.SeatPreOccupyStatus;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RedisSeatPreOccupyService redisSeatPreOccupyService;
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

        this(
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
                null,
                eventPublisher
        );
    }

    @Autowired
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
            RedisSeatPreOccupyService redisSeatPreOccupyService,
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
        this.redisSeatPreOccupyService = redisSeatPreOccupyService;
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
                    "account is banned"
            );
        }

        String requestId = normalizeRequestId(request.getRequestId());

        if (requestId != null) {
            Reservation existingReservation =
                    reservationMapper.findByUserIdAndRequestId(
                            userId,
                            requestId
                    );
            if (existingReservation != null) {
                return toCreateResponse(existingReservation);
            }
        }

        LocalDate today = LocalDate.now();

        if (request.getReservationDate().isBefore(today)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "cannot reserve a past date"
            );
        }

        Seat seat = seatMapper.findById(request.getSeatId());

        if (seat == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "seat does not exist"
            );
        }

        if (!request.getRoomId().equals(seat.getRoomId())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "seat does not belong to room"
            );
        }

        if (Integer.valueOf(BizConstants.SEAT_STATUS_REPAIR)
                .equals(seat.getStatus())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "seat is under repair"
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
                    "study room is unavailable"
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
                    "cannot reserve a slot that has already started"
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
                    "reservation duration exceeds limit"
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
                    "seat is already reserved in selected time range"
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
                    "selected time range conflicts with existing reservation"
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
                    "daily reservation limit reached"
            );
        }

        boolean preOccupied =
                preOccupySeat(
                        requestId,
                        userId,
                        request,
                        slotRange.slotIds()
                );

        try {
            Reservation reservation = new Reservation();
            reservation.setRequestId(requestId);
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

            try {
                reservationMapper.insert(reservation);
            } catch (DuplicateKeyException ex) {
                CreateReservationResponse existing =
                        tryReturnExistingByRequestId(
                                userId,
                                requestId
                        );
                if (existing != null) {
                    return existing;
                }
                throw ex;
            }

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
        } catch (RuntimeException ex) {
            if (preOccupied) {
                releasePreOccupy(
                        requestId,
                        userId,
                        request,
                        slotRange.slotIds()
                );
            }
            throw ex;
        }
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
                    "reservation does not exist"
            );
        }

        if (!userId.equals(reservation.getUserId())) {
            throw new BusinessException(
                    StatusCode.FORBIDDEN,
                    "can only operate own reservation"
            );
        }

        return reservation;
    }

    private boolean preOccupySeat(
            String requestId,
            Long userId,
            CreateReservationRequest request,
            List<Long> slotIds) {

        if (requestId == null || redisSeatPreOccupyService == null) {
            return false;
        }

        SeatPreOccupyResult result =
                redisSeatPreOccupyService.preOccupy(
                        requestId,
                        userId,
                        request.getRoomId(),
                        request.getReservationDate(),
                        slotIds,
                        request.getSeatId()
                );

        SeatPreOccupyStatus status = result.status();

        if (status == SeatPreOccupyStatus.PREOCCUPIED
                || status == SeatPreOccupyStatus.IDEMPOTENT_PREOCCUPIED) {
            return true;
        }

        if (status == SeatPreOccupyStatus.DISABLED
                || status == SeatPreOccupyStatus.FAILED) {
            return false;
        }

        if (status == SeatPreOccupyStatus.SEAT_CONFLICT) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "seat is already pre-occupied"
            );
        }

        if (status == SeatPreOccupyStatus.USER_CONFLICT) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "user has overlapping pre-occupied slot"
            );
        }

        if (status == SeatPreOccupyStatus.REQUEST_CONFLICT) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "requestId was reused with different payload"
            );
        }

        throw new BusinessException(
                StatusCode.PARAM_ERROR,
                "invalid Redis pre-occupy result"
        );
    }

    private void releasePreOccupy(
            String requestId,
            Long userId,
            CreateReservationRequest request,
            List<Long> slotIds) {

        if (requestId == null || redisSeatPreOccupyService == null) {
            return;
        }

        redisSeatPreOccupyService.release(
                requestId,
                userId,
                request.getRoomId(),
                request.getReservationDate(),
                slotIds,
                request.getSeatId()
        );
    }

    private CreateReservationResponse tryReturnExistingByRequestId(
            Long userId,
            String requestId) {

        if (requestId == null) {
            return null;
        }

        Reservation existing =
                reservationMapper.findByUserIdAndRequestId(
                        userId,
                        requestId
                );
        if (existing == null) {
            return null;
        }
        return toCreateResponse(existing);
    }

    private CreateReservationResponse toCreateResponse(
            Reservation reservation) {

        return new CreateReservationResponse(
                reservation.getId(),
                reservation.getStatus()
        );
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requestId.strip();
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
                    "selected seat or slot is already occupied"
            );
        }
    }
}