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
        LocalDateTime now = LocalDateTime.now();

        int handledNoShow =
                releaseCheckinTimeoutReservations(now);
        int handledCompleted =
                completeExpiredInUseReservations(now);

        return handledNoShow + handledCompleted;
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

    private int releaseCheckinTimeoutReservations(LocalDateTime now) {
        int limitMinutes = configService.getIntConfig(
                BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES,
                15
        );
        int violationLimit = configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        );

        int handled = 0;
        for (Reservation reservation : reservationsByStatus(
                ReservationStatus.PENDING_CHECKIN
        )) {
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

    private int completeExpiredInUseReservations(LocalDateTime now) {
        int handled = 0;

        for (Reservation reservation : reservationsByStatus(
                ReservationStatus.IN_USE
        )) {
            LocalDateTime endAt = LocalDateTime.of(
                    reservation.getReservationDate(),
                    reservation.getEndTime()
            );

            if (now.isBefore(endAt)) {
                continue;
            }

            if (reservationLifecycleService.completeExpiredInUse(
                    reservation,
                    now
            )) {
                handled++;
            }
        }

        return handled;
    }

    private List<Reservation> reservationsByStatus(
            ReservationStatus status) {

        List<Reservation> reservations =
                reservationMapper.findByStatus(status.code());

        return reservations == null ? List.of() : reservations;
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

        return true;
    }
}