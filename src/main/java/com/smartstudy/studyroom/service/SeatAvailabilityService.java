package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.ReservationSlotRange;
import com.smartstudy.studyroom.dto.SeatAvailabilityResponse;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.SeatAvailabilityMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeatAvailabilityService {

    private final SeatMapper seatMapper;
    private final StudyRoomMapper studyRoomMapper;
    private final SeatAvailabilityMapper seatAvailabilityMapper;
    private final ReservationSlotService reservationSlotService;

    public SeatAvailabilityService(
            SeatMapper seatMapper,
            StudyRoomMapper studyRoomMapper,
            SeatAvailabilityMapper seatAvailabilityMapper,
            ReservationSlotService reservationSlotService) {

        this.seatMapper = seatMapper;
        this.studyRoomMapper = studyRoomMapper;
        this.seatAvailabilityMapper = seatAvailabilityMapper;
        this.reservationSlotService = reservationSlotService;
    }

    public List<SeatAvailabilityResponse> findAvailableSeats(
            Long roomId,
            LocalDate reservationDate,
            Long startSlotId,
            Long endSlotId) {

        if (reservationDate == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "预约日期不能为空"
            );
        }

        LocalDate today = LocalDate.now();

        if (reservationDate.isBefore(today)) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "不能查询过去日期的可预约座位"
            );
        }

        StudyRoom room = studyRoomMapper.findById(roomId);

        /*
         * 复用创建预约时相同的时段解析逻辑：
         * 检查房间、时段启用状态、时段连续性和开放时间。
         */
        ReservationSlotRange slotRange =
                reservationSlotService.resolveSelectableRange(
                        room,
                        startSlotId,
                        endSlotId
                );

        if (reservationDate.isEqual(today)
                && !slotRange.startTime().isAfter(LocalTime.now())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "不能查询已经开始的预约时段"
            );
        }

        List<Seat> seats = seatMapper.findByRoomId(roomId);

        List<Reservation> activeReservations =
                seatAvailabilityMapper.findActiveReservationsBySlotIds(
                        roomId,
                        reservationDate,
                        slotRange.slotIds()
                );

        /*
         * 保存每个座位在目标日期和目标时间范围内的动态状态。
         *
         * 如果同一个座位存在多条历史异常数据，
         * “使用中”优先级高于“已预约”。
         */
        Map<Long, Integer> statusBySeat = new HashMap<>();

        for (Reservation reservation : activeReservations) {
            int dynamicStatus =
                    Integer.valueOf(BizConstants.RESERVATION_USING)
                            .equals(reservation.getStatus())
                            ? BizConstants.SEAT_STATUS_USING
                            : BizConstants.SEAT_STATUS_RESERVED;

            statusBySeat.merge(
                    reservation.getSeatId(),
                    dynamicStatus,
                    (currentStatus, newStatus) ->
                            Math.max(currentStatus, newStatus)
            );
        }

        /*
         * seat表中的2、3状态可能来源于其他日期或其他时段。
         * 因此座位地图不能直接相信全局状态：
         *
         * 维修状态继续保留；
         * 其他座位根据当前查询范围重新计算为
         * 空闲、已预约或使用中。
         */
        for (Seat seat : seats) {
            if (Integer.valueOf(BizConstants.SEAT_STATUS_REPAIR)
                    .equals(seat.getStatus())) {
                continue;
            }

            seat.setStatus(
                    statusBySeat.getOrDefault(
                            seat.getId(),
                            BizConstants.SEAT_STATUS_FREE
                    )
            );
        }

        return seats.stream()
                .map(seat -> new SeatAvailabilityResponse(
                        seat.getId(),
                        seat.getRoomId(),
                        seat.getSeatNo(),
                        seat.getX(),
                        seat.getY(),
                        seat.getHasPower(),
                        seat.getNearWindow(),
                        seat.getStatus()
                ))
                .toList();
    }
}
