package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.PageResult;
import com.smartstudy.studyroom.common.ReservationStatus;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.AdminReservationResponse;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminReservationService {

    private final ReservationMapper reservationMapper;
    private final ReservationSlotOccupancyMapper reservationSlotOccupancyMapper;
    private final RoomStatsService roomStatsService;

    public AdminReservationService(
            ReservationMapper reservationMapper,
            ReservationSlotOccupancyMapper reservationSlotOccupancyMapper,
            RoomStatsService roomStatsService) {
        this.reservationMapper = reservationMapper;
        this.reservationSlotOccupancyMapper =
                reservationSlotOccupancyMapper;
        this.roomStatsService = roomStatsService;
    }

    public PageResult<AdminReservationResponse> list(
            String studentNo,
            Long roomId,
            Integer status,
            String reservationDate,
            Integer pageNum,
            Integer pageSize) {

        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize =
                pageSize == null || pageSize < 1
                        ? 10
                        : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;

        long total = reservationMapper.countAdmin(
                studentNo,
                roomId,
                status,
                reservationDate
        );

        List<AdminReservationResponse> records =
                reservationMapper.findAdmin(
                        studentNo,
                        roomId,
                        status,
                        reservationDate,
                        offset,
                        safePageSize
                );

        return new PageResult<>(total, records);
    }

    @Transactional
    public void cancel(Long reservationId, String reason) {
        Reservation reservation =
                reservationMapper.findById(reservationId);
        if (reservation == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "预约记录不存在"
            );
        }

        ReservationStatus currentStatus =
                ReservationStatus.fromCode(reservation.getStatus());
        if (!currentStatus.canBeCancelledByAdmin()) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "该预约当前状态无法取消"
            );
        }

        int changed = reservationMapper.updateStatusIfCurrentIn(
                reservationId,
                ReservationStatus.adminCancellableCodes(),
                ReservationStatus.CANCELLED.code()
        );
        if (changed == 0) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "预约状态已变化，请刷新后重试"
            );
        }

        reservationSlotOccupancyMapper.deleteByReservationId(
                reservationId
        );
        roomStatsService.refreshRoomSeatStats(
                reservation.getRoomId()
        );
    }
}
