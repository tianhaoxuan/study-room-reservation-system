package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.ReservationSlotRange;
import com.smartstudy.studyroom.dto.ReservationSlotResponse;
import com.smartstudy.studyroom.entity.ReservationSlot;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationSlotMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReservationSlotService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private final ReservationSlotMapper reservationSlotMapper;
    private final StudyRoomMapper studyRoomMapper;

    public ReservationSlotService(ReservationSlotMapper reservationSlotMapper,
                                  StudyRoomMapper studyRoomMapper) {
        this.reservationSlotMapper = reservationSlotMapper;
        this.studyRoomMapper = studyRoomMapper;
    }

    public List<ReservationSlotResponse> findSelectableSlots(Long roomId) {
        StudyRoom room = requireEnabledRoom(roomId);

        return reservationSlotMapper.findEnabledWithin(
                        room.getOpenTime(),
                        room.getCloseTime()
                )
                .stream()
                .map(slot -> new ReservationSlotResponse(
                        slot.getId(),
                        slot.getSlotCode(),
                        slot.getSlotName(),
                        slot.getStartTime(),
                        slot.getEndTime()
                ))
                .toList();
    }

    public ReservationSlotRange resolveSelectableRange(
            StudyRoom room,
            Long startSlotId,
            Long endSlotId) {

        validateEnabledRoom(room);

        if (startSlotId == null || endSlotId == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "预约时段不能为空"
            );
        }

        ReservationSlot startSlot =
                reservationSlotMapper.findEnabledById(startSlotId);
        ReservationSlot endSlot =
                reservationSlotMapper.findEnabledById(endSlotId);

        if (startSlot == null || endSlot == null) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "预约时段不存在或已停用"
            );
        }

        if (startSlot.getDisplayOrder() > endSlot.getDisplayOrder()) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "开始时段不能晚于结束时段"
            );
        }

        List<ReservationSlot> selectedSlots =
                reservationSlotMapper.findEnabledByDisplayOrderRange(
                        startSlot.getDisplayOrder(),
                        endSlot.getDisplayOrder()
                );

        int expectedCount =
                endSlot.getDisplayOrder()
                        - startSlot.getDisplayOrder()
                        + 1;

        if (selectedSlots.size() != expectedCount) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "所选预约时段不连续或包含已停用时段"
            );
        }

        for (int i = 1; i < selectedSlots.size(); i++) {
            ReservationSlot previous = selectedSlots.get(i - 1);
            ReservationSlot current = selectedSlots.get(i);

            if (!previous.getEndTime().equals(current.getStartTime())) {
                throw new BusinessException(
                        StatusCode.PARAM_ERROR,
                        "所选预约时段不连续"
                );
            }
        }

        ReservationSlot first = selectedSlots.get(0);
        ReservationSlot last =
                selectedSlots.get(selectedSlots.size() - 1);

        if (first.getStartTime().isBefore(room.getOpenTime())
                || last.getEndTime().isAfter(room.getCloseTime())) {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "所选时段超出自习室开放时间"
            );
        }

        String timeSlot = first.getStartTime().format(TIME_FORMATTER)
                + "-"
                + last.getEndTime().format(TIME_FORMATTER);

        List<Long> slotIds = selectedSlots.stream()
                .map(ReservationSlot::getId)
                .toList();

        return new ReservationSlotRange(
                slotIds,
                first.getStartTime(),
                last.getEndTime(),
                timeSlot
        );
    }

    private StudyRoom requireEnabledRoom(Long roomId) {
        StudyRoom room = studyRoomMapper.findById(roomId);
        validateEnabledRoom(room);
        return room;
    }

    private void validateEnabledRoom(StudyRoom room) {
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
    }
}