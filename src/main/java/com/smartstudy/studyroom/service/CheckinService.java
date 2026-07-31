package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.StatusResponse;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CheckinService {

    private final ReservationService reservationService;
    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final ConfigService configService;
    private final RoomStatsService roomStatsService;

    public CheckinService(
            ReservationService reservationService,
            ReservationMapper reservationMapper,
            SeatMapper seatMapper,
            ConfigService configService,
            RoomStatsService roomStatsService) {
        this.reservationService = reservationService;
        this.reservationMapper = reservationMapper;
        this.seatMapper = seatMapper;
        this.configService = configService;
        this.roomStatsService = roomStatsService;
    }

    @Transactional
    public StatusResponse sign(
            Long userId,
            Long reservationId,
            String seatCode) {

        Reservation reservation =
                reservationService.requireOwnReservation(
                        userId,
                        reservationId
                );

        ReservationStatus currentStatus =
                ReservationStatus.fromCode(reservation.getStatus());
        if (!currentStatus.canTransitionTo(ReservationStatus.IN_USE)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "当前预约不能签到"
            );
        }

        Seat seat = seatMapper.findById(reservation.getSeatId());
        if (seat == null
                || !matchesSeatCode(seatCode, reservation, seat)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "扫码座位与预约座位不一致"
            );
        }

        LocalDateTime startAt = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getStartTime()
        );
        LocalDateTime now = LocalDateTime.now();
        int limitMinutes = configService.getIntConfig(
                BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES,
                15
        );

        if (now.isBefore(startAt)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "未到签到时间"
            );
        }
        if (now.isAfter(startAt.plusMinutes(limitMinutes))) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "已超过签到宽限时间"
            );
        }

        int changed = reservationMapper.markSigned(
                reservationId,
                currentStatus.code(),
                ReservationStatus.IN_USE.code(),
                now
        );
        if (changed == 0) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "预约状态已变化，请刷新后重试"
            );
        }

        roomStatsService.refreshRoomSeatStats(
                reservation.getRoomId()
        );
        return new StatusResponse(
                reservationId,
                ReservationStatus.IN_USE.code()
        );
    }

    @Transactional
    public void leave(Long userId, Long reservationId) {
        Reservation reservation =
                reservationService.requireOwnReservation(
                        userId,
                        reservationId
                );

        ReservationStatus currentStatus =
                ReservationStatus.fromCode(reservation.getStatus());
        if (!currentStatus.canTransitionTo(
                ReservationStatus.COMPLETED
        )) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "只有使用中的预约可以退座"
            );
        }

        int changed = reservationMapper.markLeft(
                reservationId,
                currentStatus.code(),
                ReservationStatus.COMPLETED.code(),
                LocalDateTime.now()
        );
        if (changed == 0) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "预约状态已变化，请刷新后重试"
            );
        }

        reservationService.releaseSlotOccupancies(reservationId);
        roomStatsService.refreshRoomSeatStats(
                reservation.getRoomId()
        );
    }

    private boolean matchesSeatCode(
            String seatCode,
            Reservation reservation,
            Seat seat) {

        String byRoomAndSeatNo =
                reservation.getRoomId() + "-" + seat.getSeatNo();
        String bySeatId = String.valueOf(seat.getId());
        String normalizedSeatCode = seatCode.trim();

        return byRoomAndSeatNo.equalsIgnoreCase(normalizedSeatCode)
                || bySeatId.equals(normalizedSeatCode);
    }
}
