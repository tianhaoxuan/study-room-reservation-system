package com.smartstudy.studyroom.dto;

import java.time.LocalTime;

public record ReservationSlotResponse(
        Long id,
        String slotCode,
        String slotName,
        LocalTime startTime,
        LocalTime endTime
) {
}