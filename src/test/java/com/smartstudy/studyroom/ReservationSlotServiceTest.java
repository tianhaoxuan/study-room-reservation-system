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