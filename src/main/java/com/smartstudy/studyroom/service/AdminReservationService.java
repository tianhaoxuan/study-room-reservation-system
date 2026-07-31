package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.PageResult;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.AdminReservationResponse;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminReservationService {

    private final ReservationMapper reservationMapper;
    private final ReservationLifecycleService reservationLifecycleService;

    public AdminReservationService(
            ReservationMapper reservationMapper,
            ReservationLifecycleService reservationLifecycleService) {

        this.reservationMapper = reservationMapper;
        this.reservationLifecycleService = reservationLifecycleService;
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
                    "\u9884\u7ea6\u8bb0\u5f55\u4e0d\u5b58\u5728"
            );
        }

        reservationLifecycleService.cancelByAdmin(reservation);
    }
}
