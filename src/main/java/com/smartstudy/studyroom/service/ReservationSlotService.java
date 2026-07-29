package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.ReservationSlotResponse;
import com.smartstudy.studyroom.entity.ReservationSlot;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationSlotMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReservationSlotService {

    private final ReservationSlotMapper reservationSlotMapper;
    private final StudyRoomMapper studyRoomMapper;

    public ReservationSlotService(ReservationSlotMapper reservationSlotMapper,
                                  StudyRoomMapper studyRoomMapper) {
        this.reservationSlotMapper = reservationSlotMapper;
        this.studyRoomMapper = studyRoomMapper;
    }

    public List<ReservationSlotResponse> findSelectableSlots(Long roomId) {
        StudyRoom room = studyRoomMapper.findById(roomId);

        if (room == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "自习室不存在"
            );
        }

        if (room.getStatus() == null || room.getStatus() != 1) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "自习室当前未开放"
            );
        }

        if (room.getOpenTime() == null || room.getCloseTime() == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "自习室开放时间未配置"
            );
        }

        List<ReservationSlot> slots = reservationSlotMapper.findEnabledWithin(
                room.getOpenTime(),
                room.getCloseTime()
        );

        return slots.stream()
                .map(slot -> new ReservationSlotResponse(
                        slot.getId(),
                        slot.getSlotCode(),
                        slot.getSlotName(),
                        slot.getStartTime(),
                        slot.getEndTime()
                ))
                .toList();
    }
}