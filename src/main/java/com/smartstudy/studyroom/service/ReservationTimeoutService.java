package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.entity.Violation;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.UserMapper;
import com.smartstudy.studyroom.mapper.ViolationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationTimeoutService {

    private static final int DEFAULT_COMPENSATION_BATCH_SIZE = 100;
    private static final Duration DEFAULT_COMPENSATION_LOOK_BACK =
            Duration.ofHours(24);

    private final ReservationMapper reservationMapper;
    private final ReservationLifecycleService reservationLifecycleService;
    private final ViolationMapper violationMapper;
    private final UserMapper userMapper;
    private final ConfigService configService;
    private final Clock clock;

    @Autowired
    public ReservationTimeoutService(
            ReservationMapper reservationMapper,
            ReservationLifecycleService reservationLifecycleService,
            ViolationMapper violationMapper,
            UserMapper userMapper,
            ConfigService configService) {

        this(
                reservationMapper,
                reservationLifecycleService,
                violationMapper,
                userMapper,
                configService,
                Clock.systemDefaultZone()
        );
    }

    public ReservationTimeoutService(
            ReservationMapper reservationMapper,
            ReservationLifecycleService reservationLifecycleService,
            ViolationMapper violationMapper,
            UserMapper userMapper,
            ConfigService configService,
            Clock clock) {

        this.reservationMapper = reservationMapper;
        this.reservationLifecycleService = reservationLifecycleService;
        this.violationMapper = violationMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.clock = clock;
    }

    @Transactional
    public int releaseTimeoutReservations() {
        return compensateExpiredReservations(
                DEFAULT_COMPENSATION_BATCH_SIZE,
                DEFAULT_COMPENSATION_LOOK_BACK
        );
    }

    @Transactional
    public int compensateExpiredReservations(
            int batchSize,
            Duration lookBack) {

        LocalDateTime now = LocalDateTime.now(clock);
        int normalizedBatchSize = normalizeBatchSize(batchSize);
        Duration normalizedLookBack = normalizeLookBack(lookBack);

        int handledNoShow = compensateCheckinTimeoutReservations(
                now,
                normalizedBatchSize,
                normalizedLookBack
        );
        int handledCompleted = compensateExpiredInUseReservations(
                now,
                normalizedBatchSize,
                normalizedLookBack
        );

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
        if (!LocalDateTime.now(clock).isAfter(deadlineAt)) {
            return false;
        }

        int violationLimit = configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        );
        return handleTimeoutReservation(reservation, violationLimit);
    }

    private int compensateCheckinTimeoutReservations(
            LocalDateTime now,
            int batchSize,
            Duration lookBack) {

        int limitMinutes = configService.getIntConfig(
                BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES,
                15
        );
        int violationLimit = configService.getIntConfig(
                BizConstants.CONFIG_VIOLATION_LIMIT,
                3
        );

        LocalDateTime startAtBefore = now.minusMinutes(limitMinutes);
        LocalDateTime startAtFrom = now.minus(lookBack);

        List<Reservation> reservations =
                reservationMapper.findPendingCheckinExpiredWithin(
                        ReservationStatus.PENDING_CHECKIN.code(),
                        startAtFrom,
                        startAtBefore,
                        batchSize
                );

        int handled = 0;
        for (Reservation reservation : safeList(reservations)) {
            if (handleTimeoutReservation(reservation, violationLimit)) {
                handled++;
            }
        }

        return handled;
    }

    private int compensateExpiredInUseReservations(
            LocalDateTime now,
            int batchSize,
            Duration lookBack) {

        LocalDateTime endAtFrom = now.minus(lookBack);

        List<Reservation> reservations =
                reservationMapper.findInUseEndedWithin(
                        ReservationStatus.IN_USE.code(),
                        endAtFrom,
                        now,
                        batchSize
                );

        int handled = 0;
        for (Reservation reservation : safeList(reservations)) {
            if (reservationLifecycleService.completeExpiredInUse(
                    reservation,
                    now
            )) {
                handled++;
            }
        }

        return handled;
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

    private int normalizeBatchSize(int batchSize) {
        return batchSize <= 0
                ? DEFAULT_COMPENSATION_BATCH_SIZE
                : batchSize;
    }

    private Duration normalizeLookBack(Duration lookBack) {
        if (lookBack == null || lookBack.isZero() || lookBack.isNegative()) {
            return DEFAULT_COMPENSATION_LOOK_BACK;
        }
        return lookBack;
    }

    private List<Reservation> safeList(List<Reservation> reservations) {
        return reservations == null ? List.of() : reservations;
    }
}