package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.redis.ReservationSeatBitmapProjectionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationLifecycleService {

    private final ReservationMapper reservationMapper;
    private final ReservationSlotOccupancyMapper reservationSlotOccupancyMapper;
    private final RoomStatsService roomStatsService;
    private final ReservationSeatBitmapProjectionService bitmapProjectionService;

    public ReservationLifecycleService(
            ReservationMapper reservationMapper,
            ReservationSlotOccupancyMapper reservationSlotOccupancyMapper,
            RoomStatsService roomStatsService,
            ReservationSeatBitmapProjectionService bitmapProjectionService) {

        this.reservationMapper = reservationMapper;
        this.reservationSlotOccupancyMapper =
                reservationSlotOccupancyMapper;
        this.roomStatsService = roomStatsService;
        this.bitmapProjectionService = bitmapProjectionService;
    }

    public void cancelByUser(Reservation reservation) {
        ReservationStatus currentStatus = currentStatus(reservation);
        if (!currentStatus.canTransitionTo(ReservationStatus.CANCELLED)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "只有待签到预约可以取消"
            );
        }

        updateStatus(
                reservation.getId(),
                currentStatus,
                ReservationStatus.CANCELLED
        );
        releaseAndRefresh(reservation);
    }

    public void cancelByAdmin(Reservation reservation) {
        ReservationStatus currentStatus = currentStatus(reservation);
        if (!currentStatus.canBeCancelledByAdmin()) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "该预约当前状态无法取消"
            );
        }

        int changed = reservationMapper.updateStatusIfCurrentIn(
                reservation.getId(),
                ReservationStatus.adminCancellableCodes(),
                ReservationStatus.CANCELLED.code()
        );
        ensureChanged(changed);
        releaseAndRefresh(reservation);
    }

    public void markSigned(
            Reservation reservation,
            LocalDateTime signTime) {

        ReservationStatus currentStatus = currentStatus(reservation);
        if (!currentStatus.canTransitionTo(ReservationStatus.IN_USE)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "当前预约不能签到"
            );
        }

        int changed = reservationMapper.markSigned(
                reservation.getId(),
                currentStatus.code(),
                ReservationStatus.IN_USE.code(),
                signTime
        );
        ensureChanged(changed);
        roomStatsService.refreshRoomSeatStats(reservation.getRoomId());
    }

    public void completeByLeave(
            Reservation reservation,
            LocalDateTime leaveTime) {

        ReservationStatus currentStatus = currentStatus(reservation);
        if (!currentStatus.canTransitionTo(ReservationStatus.COMPLETED)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "只有使用中的预约可以退座"
            );
        }

        int changed = reservationMapper.markLeft(
                reservation.getId(),
                currentStatus.code(),
                ReservationStatus.COMPLETED.code(),
                leaveTime
        );
        ensureChanged(changed);
        releaseAndRefresh(reservation);
    }

    public boolean violateNoShow(Reservation reservation) {
        int changed = reservationMapper.updateStatusIfCurrent(
                reservation.getId(),
                ReservationStatus.PENDING_CHECKIN.code(),
                ReservationStatus.VIOLATED.code()
        );
        if (changed == 0) {
            return false;
        }

        releaseAndRefresh(reservation);
        return true;
    }

    private ReservationStatus currentStatus(Reservation reservation) {
        return ReservationStatus.fromCode(reservation.getStatus());
    }

    private void updateStatus(
            Long reservationId,
            ReservationStatus currentStatus,
            ReservationStatus targetStatus) {

        int changed = reservationMapper.updateStatusIfCurrent(
                reservationId,
                currentStatus.code(),
                targetStatus.code()
        );
        ensureChanged(changed);
    }

    private void releaseAndRefresh(Reservation reservation) {
        List<ReservationSlotOccupancy> occupancies =
                reservationSlotOccupancyMapper.findByReservationId(
                        reservation.getId()
                );

        reservationSlotOccupancyMapper.deleteByReservationId(
                reservation.getId()
        );

        bitmapProjectionService.projectReleasedAfterCommit(
                occupancies
        );

        roomStatsService.refreshRoomSeatStats(reservation.getRoomId());
    }

    private void ensureChanged(int changed) {
        if (changed == 0) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "预约状态已经变化，请刷新后重试"
            );
        }
    }
}