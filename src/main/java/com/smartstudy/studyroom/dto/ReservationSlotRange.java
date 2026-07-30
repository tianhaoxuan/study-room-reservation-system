package com.smartstudy.studyroom.dto;

import java.time.LocalTime;
import java.util.List;

public record ReservationSlotRange(
        List<Long> slotIds,
        LocalTime startTime,
        LocalTime endTime,
        String timeSlot
) {

    public ReservationSlotRange {
        slotIds = List.copyOf(slotIds);
    }
}