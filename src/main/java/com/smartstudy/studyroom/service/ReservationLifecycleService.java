package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReservationLifecycleService {

    private final ReservationMapper reservationMapper;
    private final ReservationSlotOccupancyMapper reservationSlotOccupancyMapper;
    private final RoomStatsService roomStatsService;

    public ReservationLifecycleService(
            ReservationMapper reservationMapper,
            ReservationSlotOccupancyMapper reservationSlotOccupancyMapper,
            RoomStatsService roomStatsService) {

        this.reservationMapper = reservationMapper;
        this.reservationSlotOccupancyMapper =
                reservationSlotOccupancyMapper;
        this.roomStatsService = roomStatsService;
    }

    public void cancelByUser(Reservation reservation) {
        ReservationStatus currentStatus = currentStatus(reservation);
        if (!currentStatus.canTransitionTo(ReservationStatus.CANCELLED)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u53ea\u6709\u5f85\u7b7e\u5230\u9884\u7ea6\u53ef\u4ee5\u53d6\u6d88"
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
                    "\u8be5\u9884\u7ea6\u5f53\u524d\u72b6\u6001\u65e0\u6cd5\u53d6\u6d88"
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
                    "\u5f53\u524d\u9884\u7ea6\u4e0d\u80fd\u7b7e\u5230"
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
                    "\u53ea\u6709\u4f7f\u7528\u4e2d\u7684\u9884\u7ea6\u53ef\u4ee5\u9000\u5ea7"
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
        reservationSlotOccupancyMapper.deleteByReservationId(
                reservation.getId()
        );
        roomStatsService.refreshRoomSeatStats(reservation.getRoomId());
    }

    private void ensureChanged(int changed) {
        if (changed == 0) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "\u9884\u7ea6\u72b6\u6001\u5df2\u7ecf\u53d8\u5316\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5"
            );
        }
    }
}
