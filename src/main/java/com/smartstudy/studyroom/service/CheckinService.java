package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.StatusResponse;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.SeatMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CheckinService {

    private final ReservationService reservationService;
    private final SeatMapper seatMapper;
    private final ConfigService configService;
    private final ReservationLifecycleService reservationLifecycleService;

    public CheckinService(
            ReservationService reservationService,
            SeatMapper seatMapper,
            ConfigService configService,
            ReservationLifecycleService reservationLifecycleService) {

        this.reservationService = reservationService;
        this.seatMapper = seatMapper;
        this.configService = configService;
        this.reservationLifecycleService = reservationLifecycleService;
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
                    "\u5f53\u524d\u9884\u7ea6\u4e0d\u80fd\u7b7e\u5230"
            );
        }

        Seat seat = seatMapper.findById(reservation.getSeatId());
        if (seat == null
                || !matchesSeatCode(seatCode, reservation, seat)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u626b\u7801\u5ea7\u4f4d\u4e0e\u9884\u7ea6\u5ea7\u4f4d\u4e0d\u4e00\u81f4"
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
                    "\u672a\u5230\u7b7e\u5230\u65f6\u95f4"
            );
        }
        if (now.isAfter(startAt.plusMinutes(limitMinutes))) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u5df2\u8d85\u8fc7\u7b7e\u5230\u5bbd\u9650\u65f6\u95f4"
            );
        }

        reservationLifecycleService.markSigned(reservation, now);

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

        reservationLifecycleService.completeByLeave(
                reservation,
                LocalDateTime.now()
        );
    }

    private boolean matchesSeatCode(
            String seatCode,
            Reservation reservation,
            Seat seat) {

        if (seatCode == null) {
            return false;
        }

        String byRoomAndSeatNo =
                reservation.getRoomId() + "-" + seat.getSeatNo();
        String bySeatId = String.valueOf(seat.getId());
        String normalizedSeatCode = seatCode.trim();

        return byRoomAndSeatNo.equalsIgnoreCase(normalizedSeatCode)
                || bySeatId.equals(normalizedSeatCode);
    }
}
