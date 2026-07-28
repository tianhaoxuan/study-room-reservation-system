package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RoomStatsService {

    private final SeatMapper seatMapper;
    private final StudyRoomMapper studyRoomMapper;

    public RoomStatsService(SeatMapper seatMapper, StudyRoomMapper studyRoomMapper) {
        this.seatMapper = seatMapper;
        this.studyRoomMapper = studyRoomMapper;
    }

    /**
     * 功能：刷新自习室已预约座位数和入座率。
     * 请求参数：roomId 自习室ID。
     * 返回值：无。
     * 核心逻辑说明：统计座位表中已预约/使用中座位，按总座位数计算 occupancy_rate。
     */
    public void refreshRoomSeatStats(Long roomId) {
        StudyRoom room = studyRoomMapper.findById(roomId);
        if (room == null) {
            return;
        }
        int reservedSeats = seatMapper.countReservedOrUsingByRoomId(roomId);
        int totalSeats = room.getTotalSeats() == null ? 0 : room.getTotalSeats();
        BigDecimal occupancyRate = BigDecimal.ZERO;
        if (totalSeats > 0) {
            occupancyRate = BigDecimal.valueOf(reservedSeats)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalSeats), 2, RoundingMode.HALF_UP);
        }
        studyRoomMapper.updateSeatStats(roomId, reservedSeats, occupancyRate);
    }
}
