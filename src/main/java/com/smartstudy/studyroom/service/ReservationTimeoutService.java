package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.entity.Violation;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.UserMapper;
import com.smartstudy.studyroom.mapper.ViolationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationTimeoutService {

    private final ReservationMapper reservationMapper;
    private final ReservationLifecycleService reservationLifecycleService;
    private final ViolationMapper violationMapper;
    private final UserMapper userMapper;
    private final ConfigService configService;

    public ReservationTimeoutService(
            ReservationMapper reservationMapper,
            ReservationLifecycleService reservationLifecycleService,
            ViolationMapper violationMapper,
            UserMapper userMapper,
            ConfigService configService) {

        this.reservationMapper = reservationMapper;
        this.reservationLifecycleService = reservationLifecycleService;
        this.violationMapper = violationMapper;
        this.userMapper = userMapper;
        this.configService = configService;
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

            if (handleTimeoutReservation(reservation, violationLimit)) {
                handled++;
            }
        }

        return handled;
    }

    @Transactional
    public boolean handleCheckinTimeoutMessage(
            Long reservationId,
            LocalDateTime deadlineAt) {

        Reservation reservation = reservationMapper.findById(reservationId);
        if (reservation == null) {
            return false;
        }
        if (reservation.getStatus() == null
                || reservation.getStatus()
                != ReservationStatus.PENDING_CHECKIN.code()) {
            return false;
        }
        if (!LocalDateTime.now().isAfter(deadlineAt)) {
            return false;
        }

        int violationLimit = configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        );
        return handleTimeoutReservation(reservation, violationLimit);
    }

    private boolean handleTimeoutReservation(
            Reservation reservation,
            int violationLimit) {

        if (!reservationLifecycleService.violateNoShow(reservation)) {
            return false;
        }

        Violation violation = new Violation();
        violation.setUserId(reservation.getUserId());
        violation.setReservationId(reservation.getId());
        violation.setViolationType(
                BizConstants.VIOLATION_TIMEOUT_CHECKIN
        );
        violation.setReason("\u8d85\u65f6\u672a\u7b7e\u5230");
        violation.setHandleResult("\u8bb0\u5f55\u8fdd\u89c4\u4e00\u6b21");
        violationMapper.insert(violation);

        userMapper.increaseViolation(reservation.getUserId());
        User user = userMapper.findById(reservation.getUserId());
        if (user != null
                && user.getViolationCount() != null
                && user.getViolationCount() >= violationLimit) {
            userMapper.banUser(reservation.getUserId());
        }

        return true;
    }
}
