package com.smartstudy.studyroom;

import com.smartstudy.studyroom.dto.ReservationSlotResponse;
import com.smartstudy.studyroom.entity.ReservationSlot;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationSlotMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import com.smartstudy.studyroom.service.ReservationSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.smartstudy.studyroom.dto.ReservationSlotRange;
class ReservationSlotServiceTest {

    private ReservationSlotMapper reservationSlotMapper;
    private StudyRoomMapper studyRoomMapper;
    private ReservationSlotService reservationSlotService;

    @BeforeEach
    void setUp() {
        reservationSlotMapper = mock(ReservationSlotMapper.class);
        studyRoomMapper = mock(StudyRoomMapper.class);
        reservationSlotService = new ReservationSlotService(
                reservationSlotMapper,
                studyRoomMapper
        );
    }

    @Test
    void returnsSlotsWithinRoomOpeningHours() {
        StudyRoom room = enabledRoom(
                1L,
                LocalTime.of(8, 0),
                LocalTime.of(22, 30)
        );

        ReservationSlot firstSlot = slot(
                2L,
                "S002",
                "08:00-08:30",
                LocalTime.of(8, 0),
                LocalTime.of(8, 30)
        );

        ReservationSlot secondSlot = slot(
                3L,
                "S003",
                "08:30-09:00",
                LocalTime.of(8, 30),
                LocalTime.of(9, 0)
        );

        when(studyRoomMapper.findById(1L)).thenReturn(room);
        when(reservationSlotMapper.findEnabledWithin(
                LocalTime.of(8, 0),
                LocalTime.of(22, 30)
        )).thenReturn(List.of(firstSlot, secondSlot));

        List<ReservationSlotResponse> result =
                reservationSlotService.findSelectableSlots(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2L);
        assertThat(result.get(0).slotCode()).isEqualTo("S002");
        assertThat(result.get(0).slotName()).isEqualTo("08:00-08:30");
        assertThat(result.get(1).slotCode()).isEqualTo("S003");

        verify(reservationSlotMapper).findEnabledWithin(
                LocalTime.of(8, 0),
                LocalTime.of(22, 30)
        );
    }

    @Test
    void rejectsMissingRoom() {
        when(studyRoomMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(
                () -> reservationSlotService.findSelectableSlots(999L)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage("自习室不存在");

        verifyNoInteractions(reservationSlotMapper);
    }

    @Test
    void rejectsDisabledRoom() {
        StudyRoom room = enabledRoom(
                1L,
                LocalTime.of(8, 0),
                LocalTime.of(22, 30)
        );
        room.setStatus(0);

        when(studyRoomMapper.findById(1L)).thenReturn(room);

        assertThatThrownBy(
                () -> reservationSlotService.findSelectableSlots(1L)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage("自习室当前未开放");

        verifyNoInteractions(reservationSlotMapper);
    }
    @Test
    void resolvesContinuousSlotRange() {
        StudyRoom room = enabledRoom(
                1L,
                LocalTime.of(8, 0),
                LocalTime.of(22, 30)
        );

        ReservationSlot slot2 = orderedSlot(
                2L, "S002", "08:00-08:30",
                LocalTime.of(8, 0), LocalTime.of(8, 30), 2
        );
        ReservationSlot slot3 = orderedSlot(
                3L, "S003", "08:30-09:00",
                LocalTime.of(8, 30), LocalTime.of(9, 0), 3
        );
        ReservationSlot slot4 = orderedSlot(
                4L, "S004", "09:00-09:30",
                LocalTime.of(9, 0), LocalTime.of(9, 30), 4
        );
        ReservationSlot slot5 = orderedSlot(
                5L, "S005", "09:30-10:00",
                LocalTime.of(9, 30), LocalTime.of(10, 0), 5
        );

        when(reservationSlotMapper.findEnabledById(2L))
                .thenReturn(slot2);
        when(reservationSlotMapper.findEnabledById(5L))
                .thenReturn(slot5);
        when(reservationSlotMapper.findEnabledByDisplayOrderRange(2, 5))
                .thenReturn(List.of(slot2, slot3, slot4, slot5));

        ReservationSlotRange result =
                reservationSlotService.resolveSelectableRange(
                        room,
                        2L,
                        5L
                );

        assertThat(result.slotIds())
                .containsExactly(2L, 3L, 4L, 5L);
        assertThat(result.startTime())
                .isEqualTo(LocalTime.of(8, 0));
        assertThat(result.endTime())
                .isEqualTo(LocalTime.of(10, 0));
        assertThat(result.timeSlot())
                .isEqualTo("08:00-10:00");
    }

    @Test
    void rejectsRangeContainingMissingSlot() {
        StudyRoom room = enabledRoom(
                1L,
                LocalTime.of(8, 0),
                LocalTime.of(22, 30)
        );

        ReservationSlot slot2 = orderedSlot(
                2L, "S002", "08:00-08:30",
                LocalTime.of(8, 0), LocalTime.of(8, 30), 2
        );
        ReservationSlot slot4 = orderedSlot(
                4L, "S004", "09:00-09:30",
                LocalTime.of(9, 0), LocalTime.of(9, 30), 4
        );

        when(reservationSlotMapper.findEnabledById(2L))
                .thenReturn(slot2);
        when(reservationSlotMapper.findEnabledById(4L))
                .thenReturn(slot4);

        // 顺序2到4理论上应该存在3个时段，但这里只返回2个。
        when(reservationSlotMapper.findEnabledByDisplayOrderRange(2, 4))
                .thenReturn(List.of(slot2, slot4));

        assertThatThrownBy(
                () -> reservationSlotService.resolveSelectableRange(
                        room,
                        2L,
                        4L
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage("所选预约时段不连续或包含已停用时段");
    }

    @Test
    void rejectsRangeOutsideRoomOpeningHours() {
        StudyRoom room = enabledRoom(
                1L,
                LocalTime.of(8, 0),
                LocalTime.of(22, 30)
        );

        ReservationSlot slot1 = orderedSlot(
                1L, "S001", "07:30-08:00",
                LocalTime.of(7, 30), LocalTime.of(8, 0), 1
        );
        ReservationSlot slot2 = orderedSlot(
                2L, "S002", "08:00-08:30",
                LocalTime.of(8, 0), LocalTime.of(8, 30), 2
        );

        when(reservationSlotMapper.findEnabledById(1L))
                .thenReturn(slot1);
        when(reservationSlotMapper.findEnabledById(2L))
                .thenReturn(slot2);
        when(reservationSlotMapper.findEnabledByDisplayOrderRange(1, 2))
                .thenReturn(List.of(slot1, slot2));

        assertThatThrownBy(
                () -> reservationSlotService.resolveSelectableRange(
                        room,
                        1L,
                        2L
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage("所选时段超出自习室开放时间");
    }
    private static StudyRoom enabledRoom(Long id,
                                         LocalTime openTime,
                                         LocalTime closeTime) {
        StudyRoom room = new StudyRoom();
        room.setId(id);
        room.setStatus(1);
        room.setOpenTime(openTime);
        room.setCloseTime(closeTime);
        return room;
    }
    private static ReservationSlot orderedSlot(
            Long id,
            String slotCode,
            String slotName,
            LocalTime startTime,
            LocalTime endTime,
            Integer displayOrder) {

        ReservationSlot slot = slot(
                id,
                slotCode,
                slotName,
                startTime,
                endTime
        );
        slot.setDisplayOrder(displayOrder);
        return slot;
    }
    private static ReservationSlot slot(Long id,
                                        String slotCode,
                                        String slotName,
                                        LocalTime startTime,
                                        LocalTime endTime) {
        ReservationSlot slot = new ReservationSlot();
        slot.setId(id);
        slot.setSlotCode(slotCode);
        slot.setSlotName(slotName);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setEnabled(1);
        return slot;
    }
}