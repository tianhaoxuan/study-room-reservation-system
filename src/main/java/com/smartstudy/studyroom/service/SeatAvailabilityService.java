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
import com.smartstudy.studyroom.redis.SeatOccupancyBitmapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SeatAvailabilityService {

    private final SeatMapper seatMapper;
    private final StudyRoomMapper studyRoomMapper;
    private final SeatAvailabilityMapper seatAvailabilityMapper;
    private final ReservationSlotService reservationSlotService;
    private final SeatOccupancyBitmapService bitmapService;
    private final boolean redisProjectionEnabled;

    public SeatAvailabilityService(
            SeatMapper seatMapper,
            StudyRoomMapper studyRoomMapper,
            SeatAvailabilityMapper seatAvailabilityMapper,
            ReservationSlotService reservationSlotService,
            SeatOccupancyBitmapService bitmapService,
            @Value("${studyroom.redis.seat-occupancy.enabled:true}")
            boolean redisProjectionEnabled) {

        this.seatMapper = seatMapper;
        this.studyRoomMapper = studyRoomMapper;
        this.seatAvailabilityMapper = seatAvailabilityMapper;
        this.reservationSlotService = reservationSlotService;
        this.bitmapService = bitmapService;
        this.redisProjectionEnabled = redisProjectionEnabled;
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

        Map<Long, Integer> statusBySeat =
                resolveDynamicStatusBySeat(
                        roomId,
                        reservationDate,
                        slotRange,
                        seats
                );

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

    private Map<Long, Integer> resolveDynamicStatusBySeat(
            Long roomId,
            LocalDate reservationDate,
            ReservationSlotRange slotRange,
            List<Seat> seats) {

        if (redisProjectionEnabled) {
            List<Long> seatIds =
                    seats.stream()
                            .map(Seat::getId)
                            .toList();

            return bitmapService.findOccupiedSeatIds(
                            roomId,
                            reservationDate,
                            slotRange.slotIds(),
                            seatIds
                    )
                    .map(this::reservedStatusBySeat)
                    .orElseGet(() -> queryMysqlStatusBySeat(
                            roomId,
                            reservationDate,
                            slotRange
                    ));
        }

        return queryMysqlStatusBySeat(
                roomId,
                reservationDate,
                slotRange
        );
    }

    private Map<Long, Integer> reservedStatusBySeat(
            Set<Long> occupiedSeatIds) {

        Map<Long, Integer> statusBySeat = new HashMap<>();

        for (Long seatId : occupiedSeatIds) {
            statusBySeat.put(
                    seatId,
                    BizConstants.SEAT_STATUS_RESERVED
            );
        }

        return statusBySeat;
    }

    private Map<Long, Integer> queryMysqlStatusBySeat(
            Long roomId,
            LocalDate reservationDate,
            ReservationSlotRange slotRange) {

        List<Reservation> activeReservations =
                seatAvailabilityMapper.findActiveReservationsBySlotIds(
                        roomId,
                        reservationDate,
                        slotRange.slotIds()
                );

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
                    Math::max
            );
        }

        return statusBySeat;
    }
}