package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.entity.Violation;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.UserMapper;
import com.smartstudy.studyroom.mapper.ViolationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationTimeoutService {

    private final ReservationMapper reservationMapper;
    private final ReservationSlotOccupancyMapper reservationSlotOccupancyMapper;
    private final ViolationMapper violationMapper;
    private final UserMapper userMapper;
    private final ConfigService configService;
    private final RoomStatsService roomStatsService;

    public ReservationTimeoutService(
            ReservationMapper reservationMapper,
            ReservationSlotOccupancyMapper reservationSlotOccupancyMapper,
            ViolationMapper violationMapper,
            UserMapper userMapper,
            ConfigService configService,
            RoomStatsService roomStatsService) {
        this.reservationMapper = reservationMapper;
        this.reservationSlotOccupancyMapper =
                reservationSlotOccupancyMapper;
        this.violationMapper = violationMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.roomStatsService = roomStatsService;
    }

    @Transactional
    public int releaseTimeoutReservations() {
        int limitMinutes = configService.getIntConfig(
                BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES,
                15
        );
        int violationLimit = configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        );
        LocalDateTime now = LocalDateTime.now();

        List<Reservation> pendingReservations =
                reservationMapper.findByStatus(
                        ReservationStatus.PENDING_CHECKIN.code()
                );

        int handled = 0;
        for (Reservation reservation : pendingReservations) {
            LocalDateTime deadline = LocalDateTime.of(
                    reservation.getReservationDate(),
                    reservation.getStartTime()
            ).plusMinutes(limitMinutes);

            if (!now.isAfter(deadline)) {
                continue;
            }

            int changed = reservationMapper.updateStatusIfCurrent(
                    reservation.getId(),
                    ReservationStatus.PENDING_CHECKIN.code(),
                    ReservationStatus.VIOLATED.code()
            );
            if (changed == 0) {
                continue;
            }

            reservationSlotOccupancyMapper.deleteByReservationId(
                    reservation.getId()
            );

            Violation violation = new Violation();
            violation.setUserId(reservation.getUserId());
            violation.setReservationId(reservation.getId());
            violation.setViolationType(
                    BizConstants.VIOLATION_TIMEOUT_CHECKIN
            );
            violation.setReason("超时未签到");
            violation.setHandleResult("记录违规一次");
            violationMapper.insert(violation);

            userMapper.increaseViolation(reservation.getUserId());
            User user = userMapper.findById(reservation.getUserId());
            if (user != null
                    && user.getViolationCount() != null
                    && user.getViolationCount() >= violationLimit) {
                userMapper.banUser(reservation.getUserId());
            }

            roomStatsService.refreshRoomSeatStats(
                    reservation.getRoomId()
            );
            handled++;
        }

        return handled;
    }
}
